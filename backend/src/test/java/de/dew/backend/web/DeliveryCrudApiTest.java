package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DeliveryCrudApiTest extends BackendTest {

    @Test
    void createsReadsUpdatesAndDeletes() throws Exception {
        long id = createDelivery(bodyWith("notes", "price included customs clearance"));

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("customer_company").asText()).isEqualTo("Example Interiors");
        assertThat(detail.get("transport_method").asText()).isEqualTo("SEA");
        assertThat(detail.get("shipping_date").asText()).isEqualTo("2026-07-02");
        assertThat(detail.get("arrival_date").asText()).isEqualTo("2026-08-21");
        assertThat(detail.get("cost_eur").decimalValue()).isEqualByComparingTo("4210.00");
        assertThat(detail.get("notes").asText()).isEqualTo("price included customs clearance");

        mvc.perform(put("/api/deliveries/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith(
                                "customer_company", "Nordwind GmbH",
                                "transport_method", "AIR",
                                "cost_eur", 9100.50,
                                "notes", "rush job, air freight premium"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer_company").value("Nordwind GmbH"))
                .andExpect(jsonPath("$.transport_method").value("AIR"))
                .andExpect(jsonPath("$.notes").value("rush job, air freight premium"));

        mvc.perform(delete("/api/deliveries/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/deliveries/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void savesWithNoAttachmentsAndNoOptionalFields() throws Exception {
        Map<String, Object> body = bodyWith("status", "TO_DO");
        body.remove("shipping_date");
        body.remove("arrival_date");
        body.remove("cost_eur");
        long id = createDelivery(body);

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("shipping_date").isNull()).isTrue();
        assertThat(detail.get("arrival_date").isNull()).isTrue();
        assertThat(detail.get("cost_eur").isNull()).isTrue();
        assertThat(detail.get("offer_price_eur").isNull()).isTrue();
        assertThat(detail.get("excel_filename").isNull()).isTrue();
        assertThat(detail.get("items")).isEmpty();
        assertThat(detail.get("pdfs")).isEmpty();
        assertThat(detail.get("has_notes").asBoolean()).isFalse();
    }

    @Test
    void withNoExcelItemSumsIsNullAndNothingIsMarkedEdited() throws Exception {
        long id = createDelivery(bodyWith(
                "volume_m3", 7.42, "gross_weight_kg", 1580, "pallet_count", 6));

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.has("item_sums")).isTrue();
        assertThat(detail.get("item_sums").isNull()).isTrue();
        assertThat(detail.get("totals_edited").get("volume_m3").asBoolean()).isFalse();
        assertThat(detail.get("totals_edited").get("gross_weight_kg").asBoolean()).isFalse();
        assertThat(detail.get("totals_edited").get("pallet_count").asBoolean()).isFalse();
        assertThat(detail.get("volume_m3").decimalValue()).isEqualByComparingTo("7.42");
    }

    @Test
    void notesSurviveMultipleLinesUnchanged() throws Exception {
        String notes = "factory shipped two weeks late\n\n"
                + "price included customs clearance\n"
                + "  - indented, tabs\tand all\n"
                + "unicode: Ningbo 宁波";
        long id = createDelivery(bodyWith("notes", notes));

        assertThat(getJson("/api/deliveries/{id}", id).get("notes").asText()).isEqualTo(notes);
        assertThat(getJson("/api/deliveries/{id}", id).get("has_notes").asBoolean()).isTrue();
    }

    @Test
    void listMarksRowsWithNotesWithoutShippingTheBody() throws Exception {
        createDelivery(bodyWith("notes", "   \n  "));
        createDelivery(bodyWith("customer_company", "Nordwind GmbH", "notes", "air freight premium"));

        JsonNode rows = getJson("/api/deliveries");
        assertThat(rows).hasSize(2);
        for (JsonNode row : rows) {
            assertThat(row.has("notes")).isFalse();
            boolean expected = row.get("customer_company").asText().equals("Nordwind GmbH");
            assertThat(row.get("has_notes").asBoolean()).isEqualTo(expected);
        }
    }

    @Test
    void missingRequiredFieldsAreOneFourHundredNamingEachField() throws Exception {
        Map<String, Object> body = validBody();
        body.remove("customer_company");
        body.remove("status");
        body.put("origin_city", "   ");

        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.status").value("required"))
                .andExpect(jsonPath("$.fields.customer_company").value("required"))
                .andExpect(jsonPath("$.fields.origin_city").value("required"));
    }

    @Test
    void anUnknownTransportMethodIsRejected() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith("transport_method", "TELEPORT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.transport_method").exists());
    }

    @Test
    void unknownIdsAreFourOhFourOnEveryVerb() throws Exception {
        mvc.perform(get("/api/deliveries/{id}", 987654))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(put("/api/deliveries/{id}", 987654)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validBody())))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/deliveries/{id}", 987654)).andExpect(status().isNotFound());
    }

    @Test
    void anErrorNeverTravelsWithATwoHundred() throws Exception {
        mvc.perform(get("/api/deliveries/{id}", 987654))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customer_company\":"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingMetaLeavesAttachmentsAlone() throws Exception {
        long id = createDelivery();
        jdbc.update("INSERT INTO delivery_pdf (delivery_id, original_filename, stored_path,"
                + " size_bytes, sort_order, uploaded_at) VALUES (?, 'note.pdf', 'gen-1.pdf', 5, 0,"
                + " '2026-07-05T09:12:00Z')", id);

        mvc.perform(put("/api/deliveries/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith("origin_city", "Shanghai"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin_city").value("Shanghai"))
                .andExpect(jsonPath("$.pdfs.length()").value(1))
                .andExpect(jsonPath("$.pdfs[0].original_filename").value("note.pdf"));
    }

    @Test
    void createdAndUpdatedTimestampsAreRecorded() throws Exception {
        long id = createDelivery();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT created_at, updated_at FROM delivery WHERE id = ?", id);
        assertThat((String) row.get("created_at")).endsWith("Z");
        assertThat((String) row.get("updated_at")).endsWith("Z");
    }
}
