package de.dew.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dew.parser.PackingListParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@Import(BackendTest.FakeParserConfiguration.class)
@AutoConfigureMockMvc
public abstract class BackendTest {

    // Far from the real date, so a service that reads the system clock fails the estimate tests.
    protected static final LocalDate TODAY = LocalDate.of(2026, 3, 31);

    private static final Path TEST_ROOT =
            Paths.get(System.getProperty("java.io.tmpdir"), "dew-backend-tests");

    @DynamicPropertySource
    static void testPaths(DynamicPropertyRegistry registry) {
        deleteRecursively(TEST_ROOT);
        Path db = TEST_ROOT.resolve("deliveries.db");
        Path uploads = TEST_ROOT.resolve("uploads");
        try {
            Files.createDirectories(uploads);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot prepare test directories under " + TEST_ROOT, e);
        }
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db + "?foreign_keys=on");
        registry.add("dew.storage-dir", uploads::toString);
    }

    @TestConfiguration
    static class FakeParserConfiguration {
        @Bean
        @Primary
        PackingListParser fakePackingListParser() {
            return new FakePackingListParser();
        }

        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.systemDefault();
            return Clock.fixed(TODAY.atTime(12, 0).atZone(zone).toInstant(), zone);
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected PackingListParser injectedParser;

    @Value("${dew.storage-dir}")
    protected String storageDir;

    protected FakePackingListParser parser() {
        return (FakePackingListParser) injectedParser;
    }

    protected Path storage() {
        return Paths.get(storageDir);
    }

    @BeforeEach
    void resetState() throws IOException {
        jdbc.update("DELETE FROM delivery_item");
        jdbc.update("DELETE FROM delivery_pdf");
        jdbc.update("DELETE FROM delivery");
        try (Stream<Path> files = Files.list(storage())) {
            for (Path p : files.toList()) {
                deleteRecursively(p);
            }
        }
        parser().reset();
    }

    protected static Map<String, Object> validBody() {
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

    protected static Map<String, Object> bodyWith(Object... keyValues) {
        Map<String, Object> body = validBody();
        for (int i = 0; i < keyValues.length; i += 2) {
            body.put((String) keyValues[i], keyValues[i + 1]);
        }
        return body;
    }

    protected long createDelivery(Map<String, Object> body) throws Exception {
        String response = mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    protected long createDelivery() throws Exception {
        return createDelivery(validBody());
    }

    protected ResultActions putDelivery(long id, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/api/deliveries/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    /** {@code PUT /api/deliveries/{id}/status}; a value may be null, unlike with Map.of. */
    protected ResultActions putStatus(long id, Object... keyValues) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            body.put((String) keyValues[i], keyValues[i + 1]);
        }
        return mvc.perform(put("/api/deliveries/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected JsonNode getJson(String url, Object... uriVars) throws Exception {
        String response = mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get(url, uriVars))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    protected List<Path> storedFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(storage())) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }

    public static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
