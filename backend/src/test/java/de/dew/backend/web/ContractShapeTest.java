package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import de.dew.backend.FakePackingListParser;
import de.dew.parser.DefaultPackingListParser;
import de.dew.parser.PackingListParser;
import de.dew.backend.config.ParserConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class ContractShapeTest extends BackendTest {

    private static final List<String> LIST_ROW_KEYS = List.of(
            "id", "customer_company", "origin_country", "origin_city",
            "destination_country", "destination_city", "transport_method", "status",
            "shipping_date", "arrival_date", "cost_eur",
            "volume_m3", "gross_weight_kg", "pallet_count",
            "has_notes", "pdf_count");

    private static final List<String> DETAIL_KEYS = List.of(
            "id", "customer_company", "origin_country", "origin_city",
            "destination_country", "destination_city", "transport_method", "status",
            "shipping_date", "arrival_date", "cost_eur",
            "volume_m3", "gross_weight_kg", "pallet_count", "has_notes",
            "origin_postal_code", "origin_street",
            "destination_postal_code", "destination_street",
            "notes", "excel_filename", "offer_price_eur", "expected_arrival_date",
            "moved_to_delivered_on", "totals_edited", "item_sums", "items", "pdfs");

    private static final List<String> ITEM_KEYS = List.of(
            "source_row", "description", "article_no",
            "length_mm", "width_mm", "height_mm",
            "pallet_count", "volume_m3", "gross_weight_kg", "net_weight_kg",
            "volume_mismatch");

    private static final List<String> PDF_KEYS = List.of(
            "id", "original_filename", "size_bytes", "sort_order", "uploaded_at");

    private static final List<String> PARSE_KEYS = List.of(
            "total_volume_m3", "total_gross_weight_kg", "total_pallet_count",
            "items", "volume_mismatch_count", "sheet_name");

    private static final List<String> TOTALS_KEYS =
            List.of("volume_m3", "gross_weight_kg", "pallet_count");

    private static final List<String> ESTIMATE_KEYS = List.of(
            "chargeable_kg", "fallback", "removed_company_names", "comparables");

    private static final List<String> COMPARABLE_KEYS = List.of(
            "id", "origin_country", "origin_city",
            "destination_country", "destination_city", "transport_method",
            "shipping_date", "expected_arrival_date", "arrival_date", "age_months",
            "volume_m3", "gross_weight_kg", "pallet_count",
            "chargeable_kg", "cost_eur", "offer_price_eur", "notes");

    private static final List<String> BOARD_CARD_KEYS = List.of(
            "id", "status", "customer_company", "origin_country", "origin_city",
            "destination_country", "destination_city", "transport_method",
            "shipping_date", "expected_arrival_date", "arrival_date", "days_late",
            "offer_price_eur");

    private static final List<String> STATUSES =
            List.of("TO_DO", "OFFERS_RECEIVED", "OFFER_ACCEPTED", "ON_THE_WAY", "DELIVERED");

    @Test
    void listRowHasExactlyTheContractsKeys() throws Exception {
        createDelivery();
        JsonNode rows = getJson("/api/deliveries");
        assertThat(rows).hasSize(1);
        assertThat(keys(rows.get(0))).containsExactlyInAnyOrderElementsOf(LIST_ROW_KEYS);
    }

    @Test
    void detailHasExactlyTheContractsKeys() throws Exception {
        long id = createDelivery();
        parser().willReturn(FakePackingListParser.sampleResult());
        mvc.perform(putExcel(id)).andExpect(status().isOk());
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id)
                        .file(new MockMultipartFile("files", "note.pdf", "application/pdf",
                                "x".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(keys(detail)).containsExactlyInAnyOrderElementsOf(DETAIL_KEYS);
        assertThat(keys(detail.get("totals_edited"))).containsExactlyInAnyOrderElementsOf(TOTALS_KEYS);
        assertThat(keys(detail.get("item_sums"))).containsExactlyInAnyOrderElementsOf(TOTALS_KEYS);
        assertThat(keys(detail.get("items").get(0))).containsExactlyInAnyOrderElementsOf(ITEM_KEYS);
        assertThat(keys(detail.get("pdfs").get(0))).containsExactlyInAnyOrderElementsOf(PDF_KEYS);
    }

    @Test
    void parseResultHasExactlyTheContractsKeysAndNoWrapper() throws Exception {
        parser().willReturn(FakePackingListParser.sampleResult());

        String body = mvc.perform(multipart("/api/excel/parse")
                        .file(new MockMultipartFile("file", "packing list.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "bytes".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode parsed = json.readTree(body);
        assertThat(keys(parsed)).containsExactlyInAnyOrderElementsOf(PARSE_KEYS);
        assertThat(keys(parsed.get("items").get(0))).containsExactlyInAnyOrderElementsOf(ITEM_KEYS);
    }

    @Test
    void figuresAreJsonNumbersAndDatesArePlainIsoStrings() throws Exception {
        long id = createDelivery(bodyWith("volume_m3", 7.42, "gross_weight_kg", 1580,
                "pallet_count", 6));
        JsonNode detail = getJson("/api/deliveries/{id}", id);

        assertThat(detail.get("cost_eur").isNumber()).isTrue();
        assertThat(detail.get("volume_m3").isNumber()).isTrue();
        assertThat(detail.get("gross_weight_kg").isNumber()).isTrue();
        assertThat(detail.get("pallet_count").isNumber()).isTrue();
        assertThat(detail.get("id").isNumber()).isTrue();

        assertThat(detail.get("shipping_date").asText()).isEqualTo("2026-07-02");
        assertThat(detail.get("arrival_date").asText()).isEqualTo("2026-08-21");
        assertThat(getJson("/api/deliveries").get(0).get("shipping_date").asText())
                .isEqualTo("2026-07-02");
    }

    @Test
    void uploadedAtIsAnIsoInstantEndingInZ() throws Exception {
        long id = createDelivery();
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id)
                        .file(new MockMultipartFile("files", "note.pdf", "application/pdf",
                                "x".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk());

        String uploadedAt = getJson("/api/deliveries/{id}", id)
                .get("pdfs").get(0).get("uploaded_at").asText();
        assertThat(uploadedAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    @Test
    void transportMethodIsTheFourUppercaseWireValues() throws Exception {
        for (String method : List.of("SEA", "AIR", "ROAD", "RAIL")) {
            long id = createDelivery(bodyWith("transport_method", method));
            assertThat(getJson("/api/deliveries/{id}", id).get("transport_method").asText())
                    .isEqualTo(method);
        }
    }

    @Test
    void estimateComparablesHaveExactlyTheContractsKeysAndNoCustomerCompany() throws Exception {
        createDelivery(bodyWith("volume_m3", 7.42, "gross_weight_kg", 1580, "pallet_count", 6,
                "notes", "rush premium"));

        JsonNode estimate = getJson("/api/estimate/comparables?transport_method=SEA"
                + "&origin_country=CN&destination_country=DE&volume_m3=12");
        assertThat(keys(estimate)).containsExactlyInAnyOrderElementsOf(ESTIMATE_KEYS);
        assertThat(estimate.get("chargeable_kg").isNumber()).isTrue();
        assertThat(estimate.get("removed_company_names").isInt()).isTrue();

        JsonNode comparable = estimate.get("comparables").get(0);
        assertThat(keys(comparable)).containsExactlyInAnyOrderElementsOf(COMPARABLE_KEYS);
        assertThat(comparable.get("age_months").isInt()).isTrue();
        assertThat(comparable.get("chargeable_kg").isNumber()).isTrue();
        assertThat(comparable.get("cost_eur").isNumber()).isTrue();
        assertThat(comparable.get("shipping_date").asText()).isEqualTo("2026-07-02");
    }

    @Test
    void boardCardHasExactlyTheContractsKeys() throws Exception {
        createDelivery(bodyWith("status", "ON_THE_WAY", "offer_price_eur", 3900.00));

        JsonNode board = getJson("/api/board");
        assertThat(board.isArray()).isTrue();
        assertThat(board).hasSize(1);
        assertThat(keys(board.get(0))).containsExactlyInAnyOrderElementsOf(BOARD_CARD_KEYS);
    }

    @Test
    void aBoardCardKeepsItsNullFields() throws Exception {
        createDelivery(bodyWith("status", "TO_DO", "shipping_date", null,
                "expected_arrival_date", null, "arrival_date", null, "offer_price_eur", null));

        JsonNode card = getJson("/api/board").get(0);
        assertThat(keys(card)).containsExactlyInAnyOrderElementsOf(BOARD_CARD_KEYS);
        assertThat(card.get("offer_price_eur").isNull()).isTrue();
        assertThat(card.get("expected_arrival_date").isNull()).isTrue();
        assertThat(card.get("days_late").isNull()).isTrue();
    }

    @Test
    void aMoveReturnsExactlyTheBoardCardKeys() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED"));

        String response = putStatus(id, "status", "ON_THE_WAY")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(keys(json.readTree(response))).containsExactlyInAnyOrderElementsOf(BOARD_CARD_KEYS);
    }

    @Test
    void statusIsTheFiveUppercaseWireValuesOnEveryShape() throws Exception {
        for (String status : STATUSES) {
            // Moved to Delivered rather than created there, which would keep it off the board.
            boolean delivered = status.equals("DELIVERED");
            long id = createDelivery(bodyWith("status", delivered ? "ON_THE_WAY" : status));
            if (delivered) {
                putStatus(id, "status", status).andExpect(status().isOk());
            }
            assertThat(getJson("/api/deliveries/{id}", id).get("status").asText()).isEqualTo(status);
        }
        assertThat(getJson("/api/deliveries").findValuesAsText("status"))
                .containsExactlyInAnyOrderElementsOf(STATUSES);
        assertThat(getJson("/api/board").findValuesAsText("status"))
                .containsExactlyInAnyOrderElementsOf(STATUSES);
    }

    @Test
    void offerPriceIsAJsonNumberAndMovedToDeliveredOnAPlainIsoDate() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY", "offer_price_eur", 3900.00));
        putStatus(id, "status", "DELIVERED").andExpect(status().isOk());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("offer_price_eur").isNumber()).isTrue();
        assertThat(detail.get("moved_to_delivered_on").asText()).isEqualTo(TODAY.toString());
        assertThat(getJson("/api/board").get(0).get("offer_price_eur").isNumber()).isTrue();
    }

    @Test
    void aDetailWithoutTheNewValuesKeepsTheirKeysAsNull() throws Exception {
        long id = createDelivery(bodyWith("status", "TO_DO", "offer_price_eur", null,
                "cost_eur", null, "shipping_date", null, "expected_arrival_date", null,
                "arrival_date", null));

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(keys(detail)).containsExactlyInAnyOrderElementsOf(DETAIL_KEYS);
        assertThat(detail.get("offer_price_eur").isNull()).isTrue();
        assertThat(detail.get("expected_arrival_date").isNull()).isTrue();
        assertThat(detail.get("moved_to_delivered_on").isNull()).isTrue();
        assertThat(detail.get("cost_eur").isNull()).isTrue();
        assertThat(keys(getJson("/api/deliveries").get(0)))
                .containsExactlyInAnyOrderElementsOf(LIST_ROW_KEYS);
    }

    @Test
    void theExpectedArrivalIsAPlainIsoDateOnDetailBoardMoveAndComparable_S10Criterion1()
            throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY",
                "expected_arrival_date", "2026-03-28", "arrival_date", null,
                "offer_price_eur", 3900.00));

        assertThat(getJson("/api/deliveries/{id}", id).get("expected_arrival_date").asText())
                .isEqualTo("2026-03-28");
        JsonNode card = getJson("/api/board").get(0);
        assertThat(card.get("expected_arrival_date").asText()).isEqualTo("2026-03-28");
        assertThat(card.get("days_late").isInt()).isTrue();
        assertThat(card.get("days_late").asInt()).isEqualTo(3);

        String moved = putStatus(id, "status", "ON_THE_WAY")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(moved).get("expected_arrival_date").asText())
                .isEqualTo("2026-03-28");

        JsonNode comparable = getJson("/api/estimate/comparables?transport_method=SEA"
                + "&origin_country=CN&destination_country=DE").get("comparables").get(0);
        assertThat(comparable.get("expected_arrival_date").asText()).isEqualTo("2026-03-28");
        assertThat(comparable.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void theListRowStaysWithoutTheExpectedArrival_F11() throws Exception {
        createDelivery(bodyWith("expected_arrival_date", "2026-08-14"));

        JsonNode row = getJson("/api/deliveries").get(0);
        assertThat(keys(row)).containsExactlyInAnyOrderElementsOf(LIST_ROW_KEYS)
                .doesNotContain("expected_arrival_date");
        assertThat(row.get("arrival_date").asText()).isEqualTo("2026-08-21");
    }

    @Test
    void parserConfigWiresTheContractImplementationClass() {
        PackingListParser wired = new ParserConfig().packingListParser();
        assertThat(wired).isInstanceOf(DefaultPackingListParser.class);
    }

    private static MockMultipartHttpServletRequestBuilder putExcel(long id) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/deliveries/{id}/excel", id);
        builder.with(request -> {
            request.setMethod(HttpMethod.PUT.name());
            return request;
        });
        return (MockMultipartHttpServletRequestBuilder) builder.file(new MockMultipartFile("file",
                "packing list.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "bytes".getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
