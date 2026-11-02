package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DeliveryStatusApiTest extends BackendTest {

    private static final List<String> STATUSES =
            List.of("TO_DO", "OFFERS_RECEIVED", "OFFER_ACCEPTED", "ON_THE_WAY", "DELIVERED");

    private static final List<String> DATES_OPTIONAL =
            List.of("TO_DO", "OFFERS_RECEIVED", "OFFER_ACCEPTED");

    private static final List<String> DATES_REQUIRED = List.of("ON_THE_WAY", "DELIVERED");

    @Test
    void everyStatusIsStoredAndReturnedOnDetailAndList_F24() throws Exception {
        for (String status : STATUSES) {
            long id = createDelivery(bodyWith("status", status));
            assertThat(getJson("/api/deliveries/{id}", id).get("status").asText()).isEqualTo(status);
        }

        JsonNode rows = getJson("/api/deliveries");
        assertThat(rows).hasSize(STATUSES.size());
        assertThat(rows.findValuesAsText("status")).containsExactlyInAnyOrderElementsOf(STATUSES);
    }

    @Test
    void theStatusIsStoredByName_F24() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFERS_RECEIVED"));

        assertThat(jdbc.queryForObject("SELECT status FROM delivery WHERE id = ?", String.class, id))
                .isEqualTo("OFFERS_RECEIVED");
    }

    @Test
    void aMissingStatusIsAFieldError_F24() throws Exception {
        Map<String, Object> body = validBody();
        body.remove("status");

        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.status").value("required"));
    }

    @Test
    void anUnknownStatusIsAFieldError_F24() throws Exception {
        for (String unknown : List.of("DONE", "delivered", "")) {
            mvc.perform(post("/api/deliveries")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(bodyWith("status", unknown))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation"))
                    .andExpect(jsonPath("$.fields.status").exists());
        }
        assertThat(getJson("/api/deliveries")).isEmpty();
    }

    @Test
    void theOfferPriceIsStoredWithTwoDecimalsAtEveryStatus_F26() throws Exception {
        for (String status : STATUSES) {
            long id = createDelivery(bodyWith("status", status, "offer_price_eur", 3900.50));
            assertThat(getJson("/api/deliveries/{id}", id).get("offer_price_eur").decimalValue())
                    .isEqualByComparingTo("3900.50");
        }
    }

    @Test
    void aNegativeOfferPriceIsAFieldErrorOnCreate_F26() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith("status", "OFFER_ACCEPTED",
                                "offer_price_eur", -0.01))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.offer_price_eur").exists());

        assertThat(getJson("/api/deliveries")).isEmpty();
    }

    @Test
    void aNegativeOfferPriceIsAFieldErrorOnUpdateAndChangesNothing_F26() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED", "offer_price_eur", 3900));

        putDelivery(id, bodyWith("status", "OFFER_ACCEPTED", "offer_price_eur", -3900))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.offer_price_eur").exists());

        assertThat(getJson("/api/deliveries/{id}", id).get("offer_price_eur").decimalValue())
                .isEqualByComparingTo("3900");
    }

    @Test
    void anOfferPriceOfZeroIsAccepted_F26() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED", "offer_price_eur", 0));

        assertThat(getJson("/api/deliveries/{id}", id).get("offer_price_eur").decimalValue())
                .isEqualByComparingTo("0");
    }

    @Test
    void theOfferPriceIsOptionalAndNullWhenNotGiven_F26() throws Exception {
        for (String status : STATUSES) {
            long id = createDelivery(bodyWith("status", status));
            assertThat(getJson("/api/deliveries/{id}", id).get("offer_price_eur").isNull()).isTrue();
        }
    }

    @Test
    void theOfferPriceCanBeChangedAndRemovedOnUpdate_F26() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED", "offer_price_eur", 3900));

        putDelivery(id, bodyWith("status", "OFFER_ACCEPTED", "offer_price_eur", 4050.25))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer_price_eur").value(4050.25));
        putDelivery(id, bodyWith("status", "OFFER_ACCEPTED", "offer_price_eur", null))
                .andExpect(status().isOk());

        assertThat(getJson("/api/deliveries/{id}", id).get("offer_price_eur").isNull()).isTrue();
    }

    @Test
    void aMissingOfferPriceNeverBlocksASave_F26() throws Exception {
        for (String status : List.of("OFFER_ACCEPTED", "ON_THE_WAY", "DELIVERED")) {
            long id = createDelivery(bodyWith("status", status, "offer_price_eur", null));
            putDelivery(id, bodyWith("status", status, "offer_price_eur", null))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void theRealPriceIsOptionalAtEveryStatus_F25() throws Exception {
        for (String status : STATUSES) {
            Map<String, Object> body = bodyWith("status", status);
            body.remove("cost_eur");
            long id = createDelivery(body);

            assertThat(getJson("/api/deliveries/{id}", id).get("cost_eur").isNull()).isTrue();
            putDelivery(id, bodyWith("status", status, "cost_eur", null))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void datesAreOptionalBeforeTheDeliveryLeaves_F25() throws Exception {
        for (String status : DATES_OPTIONAL) {
            long id = createDelivery(bodyWith("status", status, "shipping_date", null,
                    "expected_arrival_date", null, "arrival_date", null));

            JsonNode detail = getJson("/api/deliveries/{id}", id);
            assertThat(detail.get("status").asText()).isEqualTo(status);
            assertThat(detail.get("shipping_date").isNull()).isTrue();
            assertThat(detail.get("expected_arrival_date").isNull()).isTrue();
            assertThat(detail.get("arrival_date").isNull()).isTrue();
        }
    }

    @Test
    void aDeliveryWithoutDatesIsListedWithNullDates_F25() throws Exception {
        createDelivery(bodyWith("status", "TO_DO", "shipping_date", null, "arrival_date", null));

        JsonNode row = getJson("/api/deliveries").get(0);
        assertThat(row.get("shipping_date").isNull()).isTrue();
        assertThat(row.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void theExpectedAndTheRealArrivalAreStoredApartAtEveryStatus_S10Criterion1() throws Exception {
        for (String status : STATUSES) {
            long id = createDelivery(bodyWith("status", status,
                    "expected_arrival_date", "2026-08-14", "arrival_date", "2026-08-21"));

            JsonNode detail = getJson("/api/deliveries/{id}", id);
            assertThat(detail.get("expected_arrival_date").asText()).as(status)
                    .isEqualTo("2026-08-14");
            assertThat(detail.get("arrival_date").asText()).as(status).isEqualTo("2026-08-21");
        }
    }

    @Test
    void theExpectedArrivalIsOptionalAtEveryStatus_F25() throws Exception {
        for (String status : STATUSES) {
            Map<String, Object> body = bodyWith("status", status);
            body.remove("expected_arrival_date");
            long id = createDelivery(body);

            assertThat(getJson("/api/deliveries/{id}", id).get("expected_arrival_date").isNull())
                    .as(status).isTrue();
            putDelivery(id, bodyWith("status", status, "expected_arrival_date", null))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void theExpectedArrivalCanBeChangedAndRemovedOnUpdate_F25() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY", "arrival_date", null,
                "expected_arrival_date", "2026-04-10"));

        putDelivery(id, bodyWith("status", "ON_THE_WAY", "arrival_date", null,
                "expected_arrival_date", "2026-04-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expected_arrival_date").value("2026-04-17"));
        putDelivery(id, bodyWith("status", "ON_THE_WAY", "arrival_date", null,
                "expected_arrival_date", null))
                .andExpect(status().isOk());

        assertThat(getJson("/api/deliveries/{id}", id).get("expected_arrival_date").isNull())
                .isTrue();
    }

    @Test
    void anUnparseableExpectedArrivalIsAFieldError_F25() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith("status", "ON_THE_WAY",
                                "expected_arrival_date", "end of April"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.expected_arrival_date").exists());

        assertThat(getJson("/api/deliveries")).isEmpty();
    }

    @Test
    void creatingOnTheWayOrDeliveredWithoutAShippingDateNamesIt_F25() throws Exception {
        for (String status : DATES_REQUIRED) {
            mvc.perform(post("/api/deliveries")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(bodyWith("status", status,
                                    "shipping_date", null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation"))
                    .andExpect(jsonPath("$.fields.shipping_date").value("required"))
                    .andExpect(jsonPath("$.fields.arrival_date").doesNotExist());
        }
        assertThat(getJson("/api/deliveries")).isEmpty();
    }

    @Test
    void creatingOnTheWayNeedsNeitherArrival_F25() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY", "shipping_date", "2026-03-20",
                "expected_arrival_date", null, "arrival_date", null));

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("status").asText()).isEqualTo("ON_THE_WAY");
        assertThat(detail.get("shipping_date").asText()).isEqualTo("2026-03-20");
        assertThat(detail.get("expected_arrival_date").isNull()).isTrue();
        assertThat(detail.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void creatingOnTheWayWithOnlyAnExpectedArrivalIsAccepted_F25() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY",
                "expected_arrival_date", "2026-04-28", "arrival_date", null));

        assertThat(getJson("/api/deliveries/{id}", id).get("expected_arrival_date").asText())
                .isEqualTo("2026-04-28");
    }

    @Test
    void creatingOnTheWayWithoutDatesNamesOnlyTheShippingDate_F25() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith("status", "ON_THE_WAY",
                                "shipping_date", null, "expected_arrival_date", null,
                                "arrival_date", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.shipping_date").value("required"))
                .andExpect(jsonPath("$.fields.arrival_date").doesNotExist())
                .andExpect(jsonPath("$.fields.expected_arrival_date").doesNotExist());

        assertThat(getJson("/api/deliveries")).isEmpty();
    }

    @Test
    void creatingDeliveredWithoutARealArrivalNamesIt_F25() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith("status", "DELIVERED",
                                "arrival_date", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.arrival_date").value("required"))
                .andExpect(jsonPath("$.fields.shipping_date").doesNotExist());

        assertThat(getJson("/api/deliveries")).isEmpty();
    }

    @Test
    void anExpectedArrivalDoesNotStandInForTheRealOneAtDelivered_F25() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith("status", "DELIVERED",
                                "expected_arrival_date", "2026-08-14", "arrival_date", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.arrival_date").value("required"))
                .andExpect(jsonPath("$.fields.expected_arrival_date").doesNotExist());

        assertThat(getJson("/api/deliveries")).isEmpty();
    }

    @Test
    void creatingDeliveredWithoutAnExpectedArrivalIsAccepted_F25() throws Exception {
        long id = createDelivery(bodyWith("status", "DELIVERED", "expected_arrival_date", null));

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("expected_arrival_date").isNull()).isTrue();
        assertThat(detail.get("arrival_date").asText()).isEqualTo("2026-08-21");
    }

    @Test
    void creatingDeliveredWithoutBothRequiredDatesNamesBoth_F25() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith("status", "DELIVERED",
                                "shipping_date", null, "arrival_date", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.shipping_date").value("required"))
                .andExpect(jsonPath("$.fields.arrival_date").value("required"));

        assertThat(getJson("/api/deliveries")).isEmpty();
    }

    @Test
    void anUpdateToOnTheWayWithoutAShippingDateIsRefusedAndChangesNothing_F25() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED",
                "shipping_date", null, "arrival_date", null, "notes", "before"));

        putDelivery(id, bodyWith("status", "ON_THE_WAY", "shipping_date", null,
                "expected_arrival_date", "2026-04-28", "arrival_date", null, "notes", "after"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.shipping_date").value("required"))
                .andExpect(jsonPath("$.fields.arrival_date").doesNotExist());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("status").asText()).isEqualTo("OFFER_ACCEPTED");
        assertThat(detail.get("notes").asText()).isEqualTo("before");
        assertThat(detail.get("expected_arrival_date").isNull()).isTrue();
    }

    @Test
    void anUpdateToOnTheWayWithAShippingDateNeedsNoArrival_F25() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED",
                "shipping_date", "2026-04-02", "arrival_date", null));

        putDelivery(id, bodyWith("status", "ON_THE_WAY", "shipping_date", "2026-04-02",
                "expected_arrival_date", null, "arrival_date", null))
                .andExpect(status().isOk());

        assertThat(getJson("/api/deliveries/{id}", id).get("status").asText())
                .isEqualTo("ON_THE_WAY");
    }

    @Test
    void anUpdateToDeliveredWithoutARealArrivalIsRefusedAndChangesNothing_F25() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY",
                "expected_arrival_date", "2026-03-28", "arrival_date", null, "notes", "before"));

        putDelivery(id, bodyWith("status", "DELIVERED",
                "expected_arrival_date", "2026-03-28", "arrival_date", null, "notes", "after"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.arrival_date").value("required"))
                .andExpect(jsonPath("$.fields.shipping_date").doesNotExist());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("status").asText()).isEqualTo("ON_THE_WAY");
        assertThat(detail.get("notes").asText()).isEqualTo("before");
        assertThat(detail.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void anUpdateThatClearsADateAtDeliveredIsRefused_F25() throws Exception {
        long id = createDelivery();

        putDelivery(id, bodyWith("shipping_date", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.shipping_date").value("required"));

        assertThat(getJson("/api/deliveries/{id}", id).get("shipping_date").asText())
                .isEqualTo("2026-07-02");
    }

    @Test
    void anUpdateBackToAnEarlierStatusMayClearTheDates_F25() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY",
                "expected_arrival_date", "2026-04-28"));

        putDelivery(id, bodyWith("status", "OFFERS_RECEIVED",
                "shipping_date", null, "expected_arrival_date", null, "arrival_date", null))
                .andExpect(status().isOk());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("shipping_date").isNull()).isTrue();
        assertThat(detail.get("expected_arrival_date").isNull()).isTrue();
        assertThat(detail.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void savingAsDeliveredFromAnotherStatusSetsTheDayFromTheClock_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        assertThat(movedToDeliveredOn(id).isNull()).isTrue();

        putDelivery(id, bodyWith("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved_to_delivered_on").value(TODAY.toString()));

        assertThat(movedToDeliveredOn(id).asText()).isEqualTo(TODAY.toString());
    }

    @Test
    void aDeliveryCreatedAtDeliveredGetsNoDay_F27() throws Exception {
        String response = mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyWith("status", "DELIVERED"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moved_to_delivered_on").isEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();

        assertThat(movedToDeliveredOn(id).isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT moved_to_delivered_on FROM delivery WHERE id = ?",
                String.class, id)).isNull();
    }

    @Test
    void savingADeliveryCreatedAtDeliveredThatStaysDeliveredLeavesItsDayBlank_F27()
            throws Exception {
        long id = createDelivery(bodyWith("status", "DELIVERED"));

        putDelivery(id, bodyWith("status", "DELIVERED", "notes", "invoice arrived"))
                .andExpect(status().isOk());

        assertThat(movedToDeliveredOn(id).isNull()).isTrue();
    }

    @Test
    void savingAwayFromDeliveredClearsTheDay_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        putDelivery(id, bodyWith("status", "DELIVERED")).andExpect(status().isOk());

        putDelivery(id, bodyWith("status", "ON_THE_WAY")).andExpect(status().isOk());

        assertThat(movedToDeliveredOn(id).isNull()).isTrue();
    }

    @Test
    void savingAsDeliveredAgainSetsANewDay_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        putDelivery(id, bodyWith("status", "DELIVERED")).andExpect(status().isOk());
        setMovedToDeliveredOn(id, "2026-03-01");

        putDelivery(id, bodyWith("status", "OFFER_ACCEPTED")).andExpect(status().isOk());
        putDelivery(id, bodyWith("status", "DELIVERED")).andExpect(status().isOk());

        assertThat(movedToDeliveredOn(id).asText()).isEqualTo(TODAY.toString());
    }

    @Test
    void savingADeliveryThatStaysDeliveredKeepsTheDay_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        putDelivery(id, bodyWith("status", "DELIVERED")).andExpect(status().isOk());
        setMovedToDeliveredOn(id, "2026-03-27");

        putDelivery(id, bodyWith("status", "DELIVERED", "notes", "invoice arrived",
                "cost_eur", 4400.00))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved_to_delivered_on").value("2026-03-27"));

        assertThat(movedToDeliveredOn(id).asText()).isEqualTo("2026-03-27");
    }

    @Test
    void savingAnExistingRecordThatStaysDeliveredLeavesItsDayBlank_F27() throws Exception {
        long id = createDelivery();
        setMovedToDeliveredOn(id, null);

        putDelivery(id, bodyWith("notes", "edited after the update")).andExpect(status().isOk());

        assertThat(movedToDeliveredOn(id).isNull()).isTrue();
    }

    @Test
    void theDayIsNotTakenFromTheRequestBody_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));

        putDelivery(id, bodyWith("status", "DELIVERED", "moved_to_delivered_on", "2020-01-01"))
                .andExpect(status().isOk());

        assertThat(movedToDeliveredOn(id).asText()).isEqualTo(TODAY.toString());
    }

    @Test
    void aDeliveryAtAnyOtherStatusHasNoDay_F27() throws Exception {
        for (String status : DATES_OPTIONAL) {
            long id = createDelivery(bodyWith("status", status));
            assertThat(movedToDeliveredOn(id).isNull()).isTrue();
        }
        long onTheWay = createDelivery(bodyWith("status", "ON_THE_WAY"));
        assertThat(movedToDeliveredOn(onTheWay).isNull()).isTrue();
    }

    @Test
    void anExistingDeliveredRecordWithoutAnArrivalDateIsReadableAndAskedForItOnSave_S9Migration()
            throws Exception {
        long id = createDelivery();
        jdbc.update("UPDATE delivery SET arrival_date = NULL, moved_to_delivered_on = NULL,"
                + " offer_price_eur = NULL WHERE id = ?", id);

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(detail.get("arrival_date").isNull()).isTrue();
        assertThat(getJson("/api/deliveries")).hasSize(1);

        putDelivery(id, bodyWith("arrival_date", null, "notes", "edited"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.arrival_date").value("required"));
        putDelivery(id, bodyWith("arrival_date", "2026-08-21"))
                .andExpect(status().isOk());
    }

    private JsonNode movedToDeliveredOn(long id) throws Exception {
        return getJson("/api/deliveries/{id}", id).get("moved_to_delivered_on");
    }

    private void setMovedToDeliveredOn(long id, String day) {
        jdbc.update("UPDATE delivery SET moved_to_delivered_on = ? WHERE id = ?", day, id);
    }
}
