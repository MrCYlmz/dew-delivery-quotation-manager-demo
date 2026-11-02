package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoardApiTest extends BackendTest {

    private static final String BOARD = "/api/board";

    // Well before TODAY, so any save made during the test is newer.
    private static final String LONG_AGO = "2020-01-01T00:00:00Z";

    @Test
    void anEmptyArchiveGivesAnEmptyBoard_F27() throws Exception {
        assertThat(getJson(BOARD)).isEmpty();
    }

    @Test
    void everyDeliveryThatIsNotDeliveredIsOnTheBoard_F27() throws Exception {
        long toDo = createDelivery(bodyWith("status", "TO_DO",
                "shipping_date", null, "arrival_date", null));
        long offersReceived = createDelivery(bodyWith("status", "OFFERS_RECEIVED"));
        long offerAccepted = createDelivery(bodyWith("status", "OFFER_ACCEPTED"));
        long onTheWay = createDelivery(bodyWith("status", "ON_THE_WAY"));

        assertThat(ids(getJson(BOARD)))
                .containsExactlyInAnyOrder(toDo, offersReceived, offerAccepted, onTheWay);
    }

    @Test
    void aDeliveryMovedToDeliveredTodayIsOnTheBoard_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        putStatus(id, "status", "DELIVERED").andExpect(status().isOk());

        JsonNode board = getJson(BOARD);
        assertThat(ids(board)).containsExactly(id);
        assertThat(board.get(0).get("status").asText()).isEqualTo("DELIVERED");
    }

    @Test
    void aDeliveredDeliveryShowsOnItsDayAndTheSixDaysAfter_F27() throws Exception {
        long movedToday = deliveredOn(TODAY.toString());
        long movedSixDaysAgo = deliveredOn(TODAY.minusDays(6).toString());
        long movedSevenDaysAgo = deliveredOn(TODAY.minusDays(7).toString());
        long movedLongAgo = deliveredOn("2025-11-02");

        assertThat(ids(getJson(BOARD)))
                .containsExactlyInAnyOrder(movedToday, movedSixDaysAgo)
                .doesNotContain(movedSevenDaysAgo, movedLongAgo);
    }

    @Test
    void theWeekIsCountedInCalendarDaysAcrossAMonthEnd_F27() throws Exception {
        // TODAY is 2026-03-31: moved 2026-03-25 shows through 2026-03-31, 2026-03-24 is gone.
        long lastDayShown = deliveredOn("2026-03-25");
        long firstDayGone = deliveredOn("2026-03-24");

        assertThat(ids(getJson(BOARD))).contains(lastDayShown).doesNotContain(firstDayGone);
    }

    @Test
    void aDeliveredDeliveryWithoutTheDayNeverShows_F27() throws Exception {
        long existingRecord = createDelivery();
        jdbc.update("UPDATE delivery SET moved_to_delivered_on = NULL WHERE id = ?", existingRecord);
        long running = createDelivery(bodyWith("status", "OFFERS_RECEIVED"));

        assertThat(ids(getJson(BOARD))).containsExactly(running);
    }

    @Test
    void aDeliveryCreatedAtDeliveredStaysOffTheBoard_F27() throws Exception {
        long archived = createDelivery(bodyWith("status", "DELIVERED"));
        long running = createDelivery(bodyWith("status", "TO_DO"));

        assertThat(ids(getJson(BOARD))).containsExactly(running);

        putDelivery(archived, bodyWith("status", "DELIVERED", "notes", "invoice arrived"))
                .andExpect(status().isOk());

        assertThat(ids(getJson(BOARD))).containsExactly(running);
    }

    @Test
    void aDeliveryMovedAwayFromDeliveredShowsInItsNewStatus_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        putStatus(id, "status", "DELIVERED").andExpect(status().isOk());
        jdbc.update("UPDATE delivery SET moved_to_delivered_on = '2026-01-01' WHERE id = ?", id);
        assertThat(ids(getJson(BOARD))).isEmpty();

        putStatus(id, "status", "ON_THE_WAY").andExpect(status().isOk());

        JsonNode board = getJson(BOARD);
        assertThat(ids(board)).containsExactly(id);
        assertThat(board.get(0).get("status").asText()).isEqualTo("ON_THE_WAY");
    }

    @Test
    void cardsComeNewestUpdateFirst_F27() throws Exception {
        long a = createDelivery(bodyWith("status", "TO_DO"));
        long b = createDelivery(bodyWith("status", "TO_DO"));
        long c = createDelivery(bodyWith("status", "ON_THE_WAY"));
        setUpdatedAt(a, "2026-03-30T09:00:00Z");
        setUpdatedAt(b, "2026-03-28T09:00:00Z");
        setUpdatedAt(c, "2026-03-29T09:00:00Z");

        assertThat(ids(getJson(BOARD))).containsExactly(a, c, b);
    }

    @Test
    void cardsUpdatedInTheSameSecondComeHigherIdFirst_F27() throws Exception {
        long a = createDelivery(bodyWith("status", "OFFERS_RECEIVED"));
        long b = createDelivery(bodyWith("status", "OFFERS_RECEIVED"));
        long c = createDelivery(bodyWith("status", "OFFERS_RECEIVED"));
        for (long id : List.of(a, b, c)) {
            setUpdatedAt(id, "2026-03-30T09:00:00Z");
        }

        assertThat(ids(getJson(BOARD))).containsExactly(c, b, a);
    }

    @Test
    void anyFormSaveBringsTheCardToTheTop_F27() throws Exception {
        long edited = createDelivery(bodyWith("status", "OFFERS_RECEIVED"));
        long other = createDelivery(bodyWith("status", "OFFERS_RECEIVED"));
        setUpdatedAt(edited, LONG_AGO);
        setUpdatedAt(other, "2020-06-01T00:00:00Z");
        assertThat(ids(getJson(BOARD))).containsExactly(other, edited);

        putDelivery(edited, bodyWith("status", "OFFERS_RECEIVED", "notes", "second offer in"))
                .andExpect(status().isOk());

        assertThat(ids(getJson(BOARD))).containsExactly(edited, other);
    }

    @Test
    void aMoveBringsTheCardToTheTop_F27() throws Exception {
        long moved = createDelivery(bodyWith("status", "OFFERS_RECEIVED"));
        long other = createDelivery(bodyWith("status", "OFFER_ACCEPTED"));
        setUpdatedAt(moved, LONG_AGO);
        setUpdatedAt(other, "2020-06-01T00:00:00Z");

        putStatus(moved, "status", "OFFER_ACCEPTED").andExpect(status().isOk());

        assertThat(ids(getJson(BOARD))).containsExactly(moved, other);
    }

    @Test
    void aCardCarriesTheFieldsTheBoardShows_F28() throws Exception {
        long id = createDelivery(bodyWith(
                "status", "ON_THE_WAY",
                "customer_company", "Example Interiors",
                "origin_country", "CN", "origin_city", "Ningbo",
                "destination_country", "DE", "destination_city", "Munich",
                "transport_method", "SEA",
                "shipping_date", "2026-03-20",
                "expected_arrival_date", "2026-04-28", "arrival_date", null,
                "offer_price_eur", 3900.00,
                "cost_eur", 4210.00));

        JsonNode card = getJson(BOARD).get(0);

        assertThat(card.get("id").asLong()).isEqualTo(id);
        assertThat(card.get("status").asText()).isEqualTo("ON_THE_WAY");
        assertThat(card.get("customer_company").asText()).isEqualTo("Example Interiors");
        assertThat(card.get("origin_country").asText()).isEqualTo("CN");
        assertThat(card.get("origin_city").asText()).isEqualTo("Ningbo");
        assertThat(card.get("destination_country").asText()).isEqualTo("DE");
        assertThat(card.get("destination_city").asText()).isEqualTo("Munich");
        assertThat(card.get("transport_method").asText()).isEqualTo("SEA");
        assertThat(card.get("shipping_date").asText()).isEqualTo("2026-03-20");
        assertThat(card.get("expected_arrival_date").asText()).isEqualTo("2026-04-28");
        assertThat(card.get("arrival_date").isNull()).isTrue();
        assertThat(card.get("offer_price_eur").decimalValue()).isEqualByComparingTo("3900.00");
    }

    @Test
    void aDeliveredCardCarriesBothArrivals_F28() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY",
                "expected_arrival_date", "2026-03-25", "arrival_date", null));
        putStatus(id, "status", "DELIVERED", "arrival_date", "2026-03-30")
                .andExpect(status().isOk());

        JsonNode card = getJson(BOARD).get(0);

        assertThat(card.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(card.get("expected_arrival_date").asText()).isEqualTo("2026-03-25");
        assertThat(card.get("arrival_date").asText()).isEqualTo("2026-03-30");
    }

    @Test
    void aCardWithoutDatesOrOfferPriceCarriesNulls_F28() throws Exception {
        createDelivery(bodyWith("status", "OFFER_ACCEPTED", "shipping_date", null,
                "expected_arrival_date", null, "arrival_date", null, "offer_price_eur", null));

        JsonNode card = getJson(BOARD).get(0);

        assertThat(card.get("shipping_date").isNull()).isTrue();
        assertThat(card.get("expected_arrival_date").isNull()).isTrue();
        assertThat(card.get("arrival_date").isNull()).isTrue();
        assertThat(card.get("days_late").isNull()).isTrue();
        assertThat(card.get("offer_price_eur").isNull()).isTrue();
    }

    @Test
    void anOnTheWayCardPastItsExpectedArrivalCarriesTheDaysLate_F30() throws Exception {
        // One per edge of the frontend's colour levels: 1-3, 4-7, 8 and more.
        for (int daysLate : List.of(1, 3, 4, 7, 8, 30)) {
            long id = onTheWayExpected(TODAY.minusDays(daysLate).toString());

            assertThat(card(id).get("days_late").asInt()).as("%d days late", daysLate)
                    .isEqualTo(daysLate);
        }
    }

    @Test
    void daysLateIsAnIntegerCountOfCalendarDays_F30() throws Exception {
        // TODAY is 2026-03-31: 2026-02-28 is 31 days back, across February's short month end.
        long id = onTheWayExpected("2026-02-28");

        JsonNode daysLate = card(id).get("days_late");
        assertThat(daysLate.isInt()).isTrue();
        assertThat(daysLate.asInt()).isEqualTo(31);
    }

    @Test
    void aCardOnOrBeforeItsExpectedArrivalIsNotLate_F30() throws Exception {
        long arrivalDay = onTheWayExpected(TODAY.toString());
        long tomorrow = onTheWayExpected(TODAY.plusDays(1).toString());
        long nextMonth = onTheWayExpected(TODAY.plusDays(30).toString());

        assertThat(card(arrivalDay).get("days_late").isNull()).isTrue();
        assertThat(card(tomorrow).get("days_late").isNull()).isTrue();
        assertThat(card(nextMonth).get("days_late").isNull()).isTrue();
    }

    @Test
    void anOnTheWayCardWithoutAnExpectedArrivalIsNotLate_F30() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY", "shipping_date", "2026-01-10",
                "expected_arrival_date", null, "arrival_date", null));

        assertThat(card(id).get("days_late").isNull()).isTrue();
    }

    @Test
    void aCardAtAnyOtherStatusIsNotLate_F30() throws Exception {
        String longPast = TODAY.minusDays(20).toString();
        List<Long> ids = new ArrayList<>();
        for (String status : List.of("TO_DO", "OFFERS_RECEIVED", "OFFER_ACCEPTED")) {
            ids.add(createDelivery(bodyWith("status", status,
                    "expected_arrival_date", longPast, "arrival_date", null)));
        }
        long delivered = createDelivery(bodyWith("status", "ON_THE_WAY",
                "expected_arrival_date", longPast, "arrival_date", null));
        putStatus(delivered, "status", "DELIVERED", "arrival_date", TODAY.toString())
                .andExpect(status().isOk());
        ids.add(delivered);

        for (long id : ids) {
            JsonNode card = card(id);
            assertThat(card.get("days_late").isNull()).as(card.get("status").asText()).isTrue();
        }
    }

    @Test
    void daysLateFollowsTheClockNotTheRealArrival_F30() throws Exception {
        // The contract counts from today for every On the way card with an expected arrival.
        long id = createDelivery(bodyWith("status", "ON_THE_WAY",
                "expected_arrival_date", TODAY.minusDays(5).toString(),
                "arrival_date", TODAY.minusDays(2).toString()));

        assertThat(card(id).get("days_late").asInt()).isEqualTo(5);
    }

    @Test
    void aMoveToOnTheWayReturnsTheDaysLate_F30() throws Exception {
        long id = createDelivery(bodyWith("status", "OFFER_ACCEPTED",
                "expected_arrival_date", TODAY.minusDays(4).toString(), "arrival_date", null));

        putStatus(id, "status", "ON_THE_WAY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days_late").value(4));
    }

    @Test
    void aMoveAwayFromOnTheWayClearsTheDaysLate_F30() throws Exception {
        long id = onTheWayExpected(TODAY.minusDays(4).toString());

        putStatus(id, "status", "OFFER_ACCEPTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days_late").value(nullValue()));
    }

    @Test
    void movingTheExpectedArrivalLaterEndsTheDelay_F30() throws Exception {
        long id = onTheWayExpected(TODAY.minusDays(4).toString());

        putStatus(id, "status", "ON_THE_WAY", "expected_arrival_date", TODAY.plusDays(3).toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days_late").value(nullValue()));

        assertThat(card(id).get("days_late").isNull()).isTrue();
    }

    @Test
    void theBoardChangesNothing_F27() throws Exception {
        long id = createDelivery(bodyWith("status", "TO_DO"));
        setUpdatedAt(id, LONG_AGO);

        getJson(BOARD);

        assertThat(jdbc.queryForObject("SELECT updated_at FROM delivery WHERE id = ?",
                String.class, id)).isEqualTo(LONG_AGO);
    }

    /** A delivery moved to Delivered on the given day; the clock is fixed, so the day is set here. */
    private long deliveredOn(String day) throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY"));
        putStatus(id, "status", "DELIVERED").andExpect(status().isOk());
        jdbc.update("UPDATE delivery SET moved_to_delivered_on = ? WHERE id = ?", day, id);
        return id;
    }

    private long onTheWayExpected(String expectedArrival) throws Exception {
        return createDelivery(bodyWith("status", "ON_THE_WAY",
                "expected_arrival_date", expectedArrival, "arrival_date", null));
    }

    private JsonNode card(long id) throws Exception {
        for (JsonNode card : getJson(BOARD)) {
            if (card.get("id").asLong() == id) {
                return card;
            }
        }
        throw new AssertionError("card " + id + " not on the board");
    }

    private void setUpdatedAt(long id, String instant) {
        jdbc.update("UPDATE delivery SET updated_at = ? WHERE id = ?", instant, id);
    }

    private static List<Long> ids(JsonNode cards) {
        List<Long> ids = new ArrayList<>();
        cards.forEach(card -> ids.add(card.get("id").asLong()));
        return ids;
    }
}
