package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeliveryMoveApiTest extends BackendTest {

    private static final List<String> STATUSES =
            List.of("TO_DO", "OFFERS_RECEIVED", "OFFER_ACCEPTED", "ON_THE_WAY", "DELIVERED");

    private static final String LONG_AGO = "2020-01-01T00:00:00Z";

    @Test
    void aDeliveryMovesFromAnyStatusToAnyOther_F24() throws Exception {
        for (String from : STATUSES) {
            for (String to : STATUSES) {
                if (from.equals(to)) {
                    continue;
                }
                long id = createDelivery(bodyWith("status", from));

                putStatus(id, "status", to)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(id))
                        .andExpect(jsonPath("$.status").value(to));

                assertThat(getJson("/api/deliveries/{id}", id).get("status").asText())
                        .as("%s -> %s", from, to).isEqualTo(to);
            }
        }
    }

    @Test
    void movesBeforeTheDeliveryLeavesNeedNoDates_F25() throws Exception {
        long id = createDelivery(bodyWith("status", "TO_DO",
                "shipping_date", null, "arrival_date", null));

        for (String to : List.of("OFFERS_RECEIVED", "OFFER_ACCEPTED", "TO_DO")) {
            putStatus(id, "status", to).andExpect(status().isOk());
        }
    }

    @Test
    void aMoveToOnTheWayWithAShippingDateNeedsNoArrival_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED",
                "shipping_date", "2026-04-02", "expected_arrival_date", null, "arrival_date", null));

        putStatus(id, "status", "ON_THE_WAY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_THE_WAY"))
                .andExpect(jsonPath("$.expected_arrival_date").value(nullValue()))
                .andExpect(jsonPath("$.arrival_date").value(nullValue()));

        assertThat(getJson("/api/deliveries/{id}", id).get("status").asText())
                .isEqualTo("ON_THE_WAY");
    }

    @Test
    void aMoveToOnTheWayWithoutAShippingDateIsRefusedNamingOnlyIt_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED", "shipping_date", null,
                "expected_arrival_date", null, "arrival_date", null));

        putStatus(id, "status", "ON_THE_WAY")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.shipping_date").value("required"))
                .andExpect(jsonPath("$.fields.arrival_date").doesNotExist())
                .andExpect(jsonPath("$.fields.expected_arrival_date").doesNotExist());

        assertThat(getJson("/api/deliveries/{id}", id).get("status").asText())
                .isEqualTo("OFFER_ACCEPTED");
    }

    @Test
    void aMoveToDeliveredWithBothRequiredDatesMissingNamesBoth_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFERS_RECEIVED",
                "shipping_date", null, "arrival_date", null));

        putStatus(id, "status", "DELIVERED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.shipping_date").value("required"))
                .andExpect(jsonPath("$.fields.arrival_date").value("required"));
    }

    @Test
    void aMoveToDeliveredWithoutARealArrivalIsRefusedEvenWithAnExpectedOne_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY",
                "expected_arrival_date", "2026-03-28", "arrival_date", null));

        putStatus(id, "status", "DELIVERED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.arrival_date").value("required"))
                .andExpect(jsonPath("$.fields.shipping_date").doesNotExist());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("status").asText()).isEqualTo("ON_THE_WAY");
        assertThat(detail.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void aMoveToOnTheWaySavesTheShippingDateAndExpectedArrivalSent_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED", "shipping_date", null,
                "expected_arrival_date", null, "arrival_date", null));

        putStatus(id, "status", "ON_THE_WAY",
                "shipping_date", "2026-04-02", "expected_arrival_date", "2026-05-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_THE_WAY"))
                .andExpect(jsonPath("$.shipping_date").value("2026-04-02"))
                .andExpect(jsonPath("$.expected_arrival_date").value("2026-05-10"))
                .andExpect(jsonPath("$.arrival_date").value(nullValue()));

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("status").asText()).isEqualTo("ON_THE_WAY");
        assertThat(detail.get("shipping_date").asText()).isEqualTo("2026-04-02");
        assertThat(detail.get("expected_arrival_date").asText()).isEqualTo("2026-05-10");
        assertThat(detail.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void aMoveToOnTheWayWithOnlyTheShippingDateSentMoves_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED", "shipping_date", null,
                "expected_arrival_date", null, "arrival_date", null));

        putStatus(id, "status", "ON_THE_WAY", "shipping_date", "2026-04-02",
                "expected_arrival_date", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping_date").value("2026-04-02"))
                .andExpect(jsonPath("$.expected_arrival_date").value(nullValue()));
    }

    @Test
    void aRefusedMoveSavesNoneOfTheDatesSent_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED", "shipping_date", null,
                "expected_arrival_date", null, "arrival_date", null));
        jdbc.update("UPDATE delivery SET updated_at = ? WHERE id = ?", LONG_AGO, id);

        putStatus(id, "status", "DELIVERED",
                "shipping_date", "2026-04-02", "expected_arrival_date", "2026-05-10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.arrival_date").value("required"));

        Map<String, Object> row = jdbc.queryForMap("SELECT status, shipping_date,"
                + " expected_arrival_date, arrival_date, updated_at FROM delivery WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("OFFER_ACCEPTED");
        assertThat(row.get("shipping_date")).isNull();
        assertThat(row.get("expected_arrival_date")).isNull();
        assertThat(row.get("arrival_date")).isNull();
        assertThat(row.get("updated_at")).isEqualTo(LONG_AGO);
    }

    @Test
    void aMoveToDeliveredSavesTheRealArrivalAndKeepsTheExpectedOne_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY", "shipping_date", "2026-02-10",
                "expected_arrival_date", "2026-03-20", "arrival_date", null));

        putStatus(id, "status", "DELIVERED", "arrival_date", "2026-03-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrival_date").value("2026-03-30"))
                .andExpect(jsonPath("$.expected_arrival_date").value("2026-03-20"))
                .andExpect(jsonPath("$.shipping_date").value("2026-02-10"));

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(detail.get("arrival_date").asText()).isEqualTo("2026-03-30");
        assertThat(detail.get("expected_arrival_date").asText()).isEqualTo("2026-03-20");
        assertThat(detail.get("shipping_date").asText()).isEqualTo("2026-02-10");
    }

    @Test
    void aMoveToDeliveredWithTheShippingDateMissingSavesTheOneSent_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED",
                "shipping_date", null, "arrival_date", "2026-03-28"));

        putStatus(id, "status", "DELIVERED",
                "shipping_date", "2026-02-11", "arrival_date", "2026-03-28")
                .andExpect(status().isOk());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("shipping_date").asText()).isEqualTo("2026-02-11");
        assertThat(detail.get("arrival_date").asText()).isEqualTo("2026-03-28");
    }

    @Test
    void aDateLeftOutOfAMoveIsKept_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED", "shipping_date", "2026-02-10",
                "expected_arrival_date", "2026-03-18", "arrival_date", "2026-03-20"));

        putStatus(id, "status", "ON_THE_WAY").andExpect(status().isOk());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("shipping_date").asText()).isEqualTo("2026-02-10");
        assertThat(detail.get("expected_arrival_date").asText()).isEqualTo("2026-03-18");
        assertThat(detail.get("arrival_date").asText()).isEqualTo("2026-03-20");
    }

    @Test
    void aNullDateInAMoveIsKeptNotCleared_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY", "shipping_date", "2026-02-10",
                "expected_arrival_date", "2026-03-18", "arrival_date", "2026-03-20"));

        putStatus(id, "status", "DELIVERED", "shipping_date", null,
                "expected_arrival_date", null, "arrival_date", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping_date").value("2026-02-10"))
                .andExpect(jsonPath("$.expected_arrival_date").value("2026-03-18"))
                .andExpect(jsonPath("$.arrival_date").value("2026-03-20"));
    }

    @Test
    void aMoveBackwardsKeepsTheDates_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "DELIVERED", "shipping_date", "2026-02-10",
                "expected_arrival_date", "2026-03-18", "arrival_date", "2026-03-20"));

        putStatus(id, "status", "TO_DO").andExpect(status().isOk());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("status").asText()).isEqualTo("TO_DO");
        assertThat(detail.get("shipping_date").asText()).isEqualTo("2026-02-10");
        assertThat(detail.get("expected_arrival_date").asText()).isEqualTo("2026-03-18");
        assertThat(detail.get("arrival_date").asText()).isEqualTo("2026-03-20");
    }

    @Test
    void aMoveLeavesEveryOtherFieldAlone_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFERS_RECEIVED",
                "offer_price_eur", 3900.00, "cost_eur", 4210.00, "notes", "two offers in",
                "volume_m3", 7.42));

        putStatus(id, "status", "OFFER_ACCEPTED").andExpect(status().isOk());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("offer_price_eur").decimalValue()).isEqualByComparingTo("3900.00");
        assertThat(detail.get("cost_eur").decimalValue()).isEqualByComparingTo("4210.00");
        assertThat(detail.get("notes").asText()).isEqualTo("two offers in");
        assertThat(detail.get("volume_m3").decimalValue()).isEqualByComparingTo("7.42");
        assertThat(detail.get("customer_company").asText()).isEqualTo("Example Interiors");
    }

    @Test
    void aMissingOfferPriceNeverBlocksAMove_F26() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFERS_RECEIVED", "offer_price_eur", null));

        for (String to : List.of("OFFER_ACCEPTED", "ON_THE_WAY", "DELIVERED")) {
            putStatus(id, "status", to)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offer_price_eur").value(nullValue()));
        }
    }

    @Test
    void aMoveToTheCurrentStatusOnlySavesTheDatesSent_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY", "shipping_date", "2026-02-10",
                "expected_arrival_date", "2026-03-20", "arrival_date", null));

        putStatus(id, "status", "ON_THE_WAY", "expected_arrival_date", "2026-04-05")
                .andExpect(status().isOk());

        JsonNode detail = getJson("/api/deliveries/{id}", id);
        assertThat(detail.get("status").asText()).isEqualTo("ON_THE_WAY");
        assertThat(detail.get("shipping_date").asText()).isEqualTo("2026-02-10");
        assertThat(detail.get("expected_arrival_date").asText()).isEqualTo("2026-04-05");
        assertThat(detail.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void aMoveBumpsUpdatedAt_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "TO_DO"));
        jdbc.update("UPDATE delivery SET updated_at = ? WHERE id = ?", LONG_AGO, id);

        putStatus(id, "status", "OFFERS_RECEIVED").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT updated_at FROM delivery WHERE id = ?",
                String.class, id)).isNotEqualTo(LONG_AGO);
    }

    @Test
    void aMoveToDeliveredSetsTheDayFromTheClock_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));

        putStatus(id, "status", "DELIVERED").andExpect(status().isOk());

        assertThat(movedToDeliveredOn(id).asText()).isEqualTo(TODAY.toString());
    }

    @Test
    void aMoveAwayFromDeliveredClearsTheDay_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        putStatus(id, "status", "DELIVERED").andExpect(status().isOk());

        putStatus(id, "status", "ON_THE_WAY").andExpect(status().isOk());

        assertThat(movedToDeliveredOn(id).isNull()).isTrue();
    }

    @Test
    void aMoveBackToDeliveredStartsANewWeek_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        putStatus(id, "status", "DELIVERED").andExpect(status().isOk());
        setMovedToDeliveredOn(id, "2026-03-02");

        putStatus(id, "status", "OFFER_ACCEPTED").andExpect(status().isOk());
        putStatus(id, "status", "DELIVERED").andExpect(status().isOk());

        assertThat(movedToDeliveredOn(id).asText()).isEqualTo(TODAY.toString());
    }

    @Test
    void aMoveFromDeliveredToDeliveredKeepsTheDay_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        putStatus(id, "status", "DELIVERED").andExpect(status().isOk());
        setMovedToDeliveredOn(id, "2026-03-27");

        putStatus(id, "status", "DELIVERED", "arrival_date", "2026-03-26")
                .andExpect(status().isOk());

        assertThat(movedToDeliveredOn(id).asText()).isEqualTo("2026-03-27");
    }

    @Test
    void aMoveWithoutAStatusIsAFieldError_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "TO_DO"));

        putStatus(id, "arrival_date", "2026-04-05")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.status").value("required"));

        assertThat(getJson("/api/deliveries/{id}", id).get("arrival_date").asText())
                .isEqualTo("2026-08-21");
    }

    @Test
    void aMoveToAnUnknownStatusIsAFieldError_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "TO_DO"));

        putStatus(id, "status", "LOST")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.status").exists());

        assertThat(getJson("/api/deliveries/{id}", id).get("status").asText()).isEqualTo("TO_DO");
    }

    @Test
    void anUnparseableDateInAMoveIsAFieldError_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED"));

        putStatus(id, "status", "ON_THE_WAY", "arrival_date", "next week")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.arrival_date").exists());
    }

    @Test
    void anUnparseableExpectedArrivalInAMoveIsAFieldError_F29() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED"));

        putStatus(id, "status", "ON_THE_WAY", "expected_arrival_date", "next week")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.expected_arrival_date").exists());

        assertThat(getJson("/api/deliveries/{id}", id).get("status").asText())
                .isEqualTo("OFFER_ACCEPTED");
    }

    @Test
    void aMoveOfAnUnknownDeliveryIsNotFound_F29() throws Exception {
        putStatus(987654, "status", "ON_THE_WAY",
                "shipping_date", "2026-04-02", "expected_arrival_date", "2026-05-10")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    private JsonNode movedToDeliveredOn(long id) throws Exception {
        return getJson("/api/deliveries/{id}", id).get("moved_to_delivered_on");
    }

    private void setMovedToDeliveredOn(long id, String day) {
        jdbc.update("UPDATE delivery SET moved_to_delivered_on = ? WHERE id = ?", day, id);
    }
}
