package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dew.parser.DefaultPackingListParser;
import de.dew.parser.PackingListParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class RealParserEndToEndTest {

    private static final int EXPECTED_ITEMS = 20;

    @DynamicPropertySource
    static void testPaths(DynamicPropertyRegistry registry) {
        Path root = Paths.get(System.getProperty("java.io.tmpdir"), "dew-real-parser-test");
        de.dew.backend.BackendTest.deleteRecursively(root);
        try {
            Files.createDirectories(root.resolve("uploads"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot prepare " + root, e);
        }
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + root.resolve("deliveries.db") + "?foreign_keys=on");
        registry.add("dew.storage-dir", () -> root.resolve("uploads").toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    PackingListParser parser;

    @Test
    void theWiredParserIsTheRealImplementation() {
        assertThat(parser).isInstanceOf(DefaultPackingListParser.class);
    }

    @Test
    void parsesTheAcceptanceFixtureThroughTheApi() throws Exception {
        mvc.perform(multipart("/api/excel/parse").file(fixture("file")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_volume_m3").value(7.42))
                .andExpect(jsonPath("$.total_gross_weight_kg").value(1580))
                .andExpect(jsonPath("$.total_pallet_count").value(6))
                .andExpect(jsonPath("$.items.length()").value(EXPECTED_ITEMS))
                .andExpect(jsonPath("$.net_weight_kg").doesNotExist())
                .andExpect(jsonPath("$.total_net_weight_kg").doesNotExist())
                .andExpect(jsonPath("$.sheet_name").exists());
    }

    @Test
    void storesTheFixturesItemsAndReportsTheSumsOnTheDetail() throws Exception {
        long id = createDelivery();

        mvc.perform(putExcel(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(EXPECTED_ITEMS))
                .andExpect(jsonPath("$.item_sums.volume_m3").value(7.42))
                .andExpect(jsonPath("$.item_sums.gross_weight_kg").value(1580))
                .andExpect(jsonPath("$.item_sums.pallet_count").value(6))
                .andExpect(jsonPath("$.excel_filename").value("QX20260115-packing-list.xlsx"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_item WHERE delivery_id = ?",
                Integer.class, id)).isEqualTo(EXPECTED_ITEMS);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM delivery_item WHERE delivery_id = ? AND volume_mismatch = 1",
                Integer.class, id)).isZero();
    }

    @Test
    void anOverriddenTotalSurvivesARealUpload() throws Exception {
        Map<String, Object> body = body();
        body.put("volume_m3", 15.00);
        long id = createDelivery(body);

        mvc.perform(putExcel(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volume_m3").value(15.00))
                .andExpect(jsonPath("$.item_sums.volume_m3").value(7.42))
                .andExpect(jsonPath("$.totals_edited.volume_m3").value(true));
    }

    private long createDelivery() throws Exception {
        return createDelivery(body());
    }

    private long createDelivery(Map<String, Object> body) throws Exception {
        String response = mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.get("id").asLong();
    }

    private static Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customer_company", "Example Interiors");
        body.put("origin_country", "CN");
        body.put("origin_city", "Ningbo");
        body.put("destination_country", "DE");
        body.put("destination_city", "Munich");
        body.put("transport_method", "SEA");
        body.put("status", "DELIVERED");
        body.put("shipping_date", "2026-07-02");
        body.put("arrival_date", "2026-08-21");
        body.put("cost_eur", 4210.00);
        return body;
    }

    private MockMultipartHttpServletRequestBuilder putExcel(long id) throws IOException {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/deliveries/{id}/excel", id);
        builder.with(request -> {
            request.setMethod(HttpMethod.PUT.name());
            return request;
        });
        return (MockMultipartHttpServletRequestBuilder) builder.file(fixture("file"));
    }

    private static MockMultipartFile fixture(String field) throws IOException {
        Path sample = samplePath();
        return new MockMultipartFile(field, sample.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(sample));
    }

    private static Path samplePath() {
        Path fromModule = Paths.get("..", "samples", "QX20260115-packing-list.xlsx");
        if (Files.isRegularFile(fromModule)) {
            return fromModule;
        }
        Path fromRoot = Paths.get("samples", "QX20260115-packing-list.xlsx");
        if (Files.isRegularFile(fromRoot)) {
            return fromRoot;
        }
        throw new IllegalStateException("Cannot find the SPEC.md section 12 fixture; looked at "
                + fromModule.toAbsolutePath() + " and " + fromRoot.toAbsolutePath());
    }
}
