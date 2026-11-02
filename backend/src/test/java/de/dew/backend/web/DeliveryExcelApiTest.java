package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import de.dew.backend.FakePackingListParser;
import de.dew.parser.PackingListItem;
import de.dew.parser.PackingListResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class DeliveryExcelApiTest extends BackendTest {

    @Test
    void parsesWithoutTouchingTheDatabaseOrTheStorageDirectory() throws Exception {
        parser().willReturn(FakePackingListParser.sampleResult());

        mvc.perform(multipart("/api/excel/parse").file(excel("packing list.xlsx", "bytes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_volume_m3").value(7.42))
                .andExpect(jsonPath("$.total_gross_weight_kg").value(1580))
                .andExpect(jsonPath("$.total_pallet_count").value(6))
                .andExpect(jsonPath("$.volume_mismatch_count").value(1))
                .andExpect(jsonPath("$.sheet_name").value("PACKING LIST "))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].source_row").value(5))
                .andExpect(jsonPath("$.items[2].volume_mismatch").value(true))
                .andExpect(jsonPath("$.items[1].volume_m3").doesNotExist())
                .andExpect(jsonPath("$.items[1].net_weight_kg").value(120.25));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_item", Integer.class)).isZero();
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void aParseFailureIsFourTwentyTwoCarryingTheParsersMessage() throws Exception {
        String message = "No sheet matched 'packing list' and none carried the header signature";
        parser().willFail(message);

        mvc.perform(multipart("/api/excel/parse").file(excel("invoice only.xlsx", "bytes")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("parse_failed"))
                .andExpect(jsonPath("$.detail").value(message));
    }

    @Test
    void theParserSeesTheUploadedBytesAndTheOriginalFilename() throws Exception {
        parser().willReturn(FakePackingListParser.sampleResult());

        mvc.perform(multipart("/api/excel/parse").file(excel("QX20260115.xlsx", "workbook-bytes")))
                .andExpect(status().isOk());

        assertThat(parser().calls()).isEqualTo(1);
        assertThat(parser().lastFilename()).isEqualTo("QX20260115.xlsx");
        assertThat(new String(parser().lastContent(), StandardCharsets.UTF_8))
                .isEqualTo("workbook-bytes");
    }

    @Test
    void aMissingFilePartIsAValidationError() throws Exception {
        mvc.perform(multipart("/api/excel/parse"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.file").exists());
    }

    @Test
    void attachingAnExcelStoresTheFileAndTheItemRows() throws Exception {
        long id = createDelivery();
        parser().willReturn(FakePackingListParser.sampleResult());

        mvc.perform(putExcel(id, excel("QX20260115 INV & PAC.xlsx", "bytes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excel_filename").value("QX20260115 INV & PAC.xlsx"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.item_sums.volume_m3").value(7.42))
                .andExpect(jsonPath("$.item_sums.gross_weight_kg").value(1580))
                .andExpect(jsonPath("$.item_sums.pallet_count").value(6));

        assertThat(storedFiles()).hasSize(1);
        assertThat(storedFiles().get(0).getFileName().toString())
                .doesNotContain("QX20260115").endsWith(".xlsx");
    }

    @Test
    void reparsingDoesNotOverwriteAnEditedTotalAndF10ReportsTheDivergence() throws Exception {
        long id = createDelivery(bodyWith("volume_m3", 14.50, "gross_weight_kg", 1580,
                "pallet_count", 6));
        parser().willReturn(FakePackingListParser.sampleResult());

        mvc.perform(putExcel(id, excel("packing list.xlsx", "bytes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volume_m3").value(14.50))
                .andExpect(jsonPath("$.totals_edited.volume_m3").value(true))
                .andExpect(jsonPath("$.totals_edited.gross_weight_kg").value(false))
                .andExpect(jsonPath("$.totals_edited.pallet_count").value(false))
                .andExpect(jsonPath("$.item_sums.volume_m3").value(7.42));
    }

    @Test
    void replacingTheExcelReplacesTheItemsAndDeletesThePreviousFile() throws Exception {
        long id = createDelivery();
        parser().willReturn(FakePackingListParser.sampleResult());
        mvc.perform(putExcel(id, excel("first.xlsx", "first-bytes"))).andExpect(status().isOk());
        String firstPath = jdbc.queryForObject(
                "SELECT excel_path FROM delivery WHERE id = ?", String.class, id);

        parser().willReturn(new PackingListResult(new BigDecimal("1.00"), new BigDecimal("50"),
                new BigDecimal("1"), List.of(new PackingListItem(9, "Lamp", "C-1", null, null, null,
                        new BigDecimal("1"), new BigDecimal("1.00"), new BigDecimal("50"), null,
                        false)), 0, "PACKING LIST"));

        mvc.perform(putExcel(id, excel("second.xlsx", "second-bytes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excel_filename").value("second.xlsx"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Lamp"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_item WHERE delivery_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(storedFiles()).hasSize(1);
        assertThat(storage().resolve(firstPath)).doesNotExist();
    }

    @Test
    void aFailedReparseLeavesThePreviousExcelAndItemsIntact() throws Exception {
        long id = createDelivery();
        parser().willReturn(FakePackingListParser.sampleResult());
        mvc.perform(putExcel(id, excel("good.xlsx", "good-bytes"))).andExpect(status().isOk());

        parser().willFail("Required column 'Total Volume' could not be mapped");
        mvc.perform(putExcel(id, excel("bad.xlsx", "bad-bytes")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value("Required column 'Total Volume' could not be mapped"));

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("excel_filename").asText()).isEqualTo("good.xlsx");
        assertThat(detail.get("items")).hasSize(3);
        assertThat(storedFiles()).hasSize(1);
    }

    @Test
    void downloadsTheOriginalExcelAsAnAttachment() throws Exception {
        long id = createDelivery();
        parser().willReturn(FakePackingListParser.sampleResult());
        String original = "QX20260115 INV & PAC.xlsx";
        mvc.perform(putExcel(id, excel(original, "workbook"))).andExpect(status().isOk());

        byte[] body = mvc.perform(get("/api/deliveries/{id}/excel", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(header().string("Content-Length", "8"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("workbook");
    }

    @Test
    void downloadingAnAbsentExcelIsFourOhFour() throws Exception {
        long id = createDelivery();
        mvc.perform(get("/api/deliveries/{id}/excel", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void attachingToAnUnknownDeliveryIsFourOhFourAndDoesNotEvenParse() throws Exception {
        mvc.perform(putExcel(987654, excel("x.xlsx", "bytes")))
                .andExpect(status().isNotFound());
        assertThat(parser().calls()).isZero();
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void deletingADeliveryRemovesItsExcelFile() throws Exception {
        long id = createDelivery();
        parser().willReturn(FakePackingListParser.sampleResult());
        mvc.perform(putExcel(id, excel("x.xlsx", "bytes"))).andExpect(status().isOk());
        assertThat(storedFiles()).hasSize(1);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/deliveries/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(storedFiles()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_item", Integer.class)).isZero();
    }

    private static MockMultipartHttpServletRequestBuilder putExcel(long id, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder builder =
                multipart("/api/deliveries/{id}/excel", id);
        builder.with(request -> {
            request.setMethod(HttpMethod.PUT.name());
            return request;
        });
        return (MockMultipartHttpServletRequestBuilder) builder.file(file);
    }

    private static MockMultipartFile excel(String filename, String content) {
        return new MockMultipartFile("file", filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
