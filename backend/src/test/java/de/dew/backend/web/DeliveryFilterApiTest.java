package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeliveryFilterApiTest extends BackendTest {

    @BeforeEach
    void seed() throws Exception {
        seedDelivery("Example Interiors", "CN", "Ningbo", "DE", "Munich", "SEA", "DELIVERED",
                "2026-01-09", "2026-02-20", 4210.00, 12.60, 2480, 9);
        seedDelivery("Example Interiors", "CN", "Shanghai", "DE", "Munich", "AIR", "DELIVERED",
                "2026-03-15", "2026-03-20", 9100.50, 2.10, 410, 2);
        seedDelivery("Example Interiors", "VN", "Haiphong", "NL", "Rotterdam", "SEA", "OFFER_ACCEPTED",
                "2026-05-01", null, 3800.00, 20.00, 5000, 18);
        seedDelivery("Nordwind GmbH", "CN", "Ningbo", "DE", "Hamburg", "ROAD", "ON_THE_WAY",
                "2026-07-02", "2026-07-19", 1250.75, 1.00, 150, 1);
        seedDelivery("Nordwind GmbH", "CN", "Ningbo", "PL", "Gdansk", "RAIL", "OFFERS_RECEIVED",
                "2026-09-30", "2026-10-25", 2600.00, 8.40, 1900, 7);
    }

    @Test
    void defaultsToNewestShippingDateFirst() throws Exception {
        assertThat(shippingDates(getJson("/api/deliveries")))
                .containsExactly("2026-09-30", "2026-07-02", "2026-05-01", "2026-03-15", "2026-01-09");
    }

    @Test
    void filtersByCompany() throws Exception {
        assertThat(getJson("/api/deliveries?customer_company=Nordwind GmbH")).hasSize(2);
        assertThat(getJson("/api/deliveries?customer_company=Example Interiors")).hasSize(3);
        assertThat(getJson("/api/deliveries?customer_company=example interiors")).isEmpty();
    }

    @Test
    void filtersByRouteCountryAndCityIndependently() throws Exception {
        assertThat(getJson("/api/deliveries?origin_country=CN")).hasSize(4);
        assertThat(getJson("/api/deliveries?origin_city=Ningbo")).hasSize(3);
        assertThat(getJson("/api/deliveries?destination_country=DE")).hasSize(3);
        assertThat(getJson("/api/deliveries?destination_city=Munich")).hasSize(2);
    }

    @Test
    void filtersByTransportMethod() throws Exception {
        assertThat(getJson("/api/deliveries?transport_method=SEA")).hasSize(2);
        assertThat(getJson("/api/deliveries?transport_method=RAIL")).hasSize(1);
    }

    @Test
    void filtersByShippingDateRangeInclusively() throws Exception {
        assertThat(shippingDates(getJson("/api/deliveries?shipping_date_from=2026-03-15"
                + "&shipping_date_to=2026-07-02")))
                .containsExactly("2026-07-02", "2026-05-01", "2026-03-15");
        assertThat(getJson("/api/deliveries?shipping_date_from=2026-05-01"
                + "&shipping_date_to=2026-05-01")).hasSize(1);
        assertThat(getJson("/api/deliveries?shipping_date_from=2026-05-02"
                + "&shipping_date_to=2026-05-02")).isEmpty();
    }

    @Test
    void anArrivalRangeExcludesRecordsWithNoArrivalDate() throws Exception {
        JsonNode rows = getJson("/api/deliveries?arrival_date_from=2026-01-01"
                + "&arrival_date_to=2026-12-31");
        assertThat(rows).hasSize(4);
        for (JsonNode row : rows) {
            assertThat(row.get("arrival_date").isNull()).isFalse();
        }
    }

    @Test
    void filtersByCostRangeInclusively() throws Exception {
        assertThat(getJson("/api/deliveries?cost_min=2600&cost_max=4210")).hasSize(3);
        assertThat(getJson("/api/deliveries?cost_min=9100.50")).hasSize(1);
        assertThat(getJson("/api/deliveries?cost_max=1250.75")).hasSize(1);
    }

    @Test
    void filtersAreCumulativeAnd() throws Exception {
        assertThat(getJson("/api/deliveries?origin_country=CN")).hasSize(4);
        assertThat(getJson("/api/deliveries?origin_country=CN&origin_city=Ningbo")).hasSize(3);
        assertThat(getJson("/api/deliveries?origin_country=CN&origin_city=Ningbo"
                + "&customer_company=Nordwind GmbH")).hasSize(2);
        assertThat(getJson("/api/deliveries?origin_country=CN&origin_city=Ningbo"
                + "&customer_company=Nordwind GmbH&transport_method=ROAD")).hasSize(1);
    }

    @Test
    void combinesCompanyMethodDateRangeAndCostAtOnce() throws Exception {
        JsonNode rows = getJson("/api/deliveries?customer_company=Example Interiors"
                + "&transport_method=SEA&shipping_date_from=2026-01-01"
                + "&shipping_date_to=2026-04-30&cost_min=4000&cost_max=5000");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("shipping_date").asText()).isEqualTo("2026-01-09");
    }

    @Test
    void combinesBothDateRangesAtOnce() throws Exception {
        JsonNode rows = getJson("/api/deliveries?shipping_date_from=2026-01-01"
                + "&shipping_date_to=2026-06-30&arrival_date_from=2026-03-01"
                + "&arrival_date_to=2026-03-31");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("arrival_date").asText()).isEqualTo("2026-03-20");
    }

    @Test
    void aCombinationThatMatchesNothingReturnsAnEmptyArrayNotAnError() throws Exception {
        assertThat(getJson("/api/deliveries?customer_company=Nordwind GmbH"
                + "&destination_city=Rotterdam")).isEmpty();
    }

    @Test
    void sortsByEachOfTheFiveKeysInBothDirections() throws Exception {
        assertThat(field(getJson("/api/deliveries?sort=shipping_date&order=asc"), "shipping_date"))
                .containsExactly("2026-01-09", "2026-03-15", "2026-05-01", "2026-07-02", "2026-09-30");
        assertThat(field(getJson("/api/deliveries?sort=shipping_date&order=desc"), "shipping_date"))
                .containsExactly("2026-09-30", "2026-07-02", "2026-05-01", "2026-03-15", "2026-01-09");

        assertThat(numbers(getJson("/api/deliveries?sort=cost_eur&order=asc"), "cost_eur"))
                .containsExactly(1250.75, 2600.00, 3800.00, 4210.00, 9100.50);
        assertThat(numbers(getJson("/api/deliveries?sort=cost_eur&order=desc"), "cost_eur"))
                .containsExactly(9100.50, 4210.00, 3800.00, 2600.00, 1250.75);

        assertThat(numbers(getJson("/api/deliveries?sort=volume_m3&order=asc"), "volume_m3"))
                .containsExactly(1.00, 2.10, 8.40, 12.60, 20.00);
        assertThat(numbers(getJson("/api/deliveries?sort=gross_weight_kg&order=desc"),
                "gross_weight_kg")).containsExactly(5000.0, 2480.0, 1900.0, 410.0, 150.0);

        assertThat(getJson("/api/deliveries?sort=arrival_date&order=asc")).hasSize(5);
    }

    @Test
    void sortAppliesToTheFilteredSet() throws Exception {
        assertThat(numbers(getJson("/api/deliveries?customer_company=Example Interiors"
                + "&sort=cost_eur&order=asc"), "cost_eur"))
                .containsExactly(3800.00, 4210.00, 9100.50);
    }

    @Test
    void anUnknownSortKeyIsRejectedRatherThanReachingTheOrderByClause() throws Exception {
        mvc.perform(get("/api/deliveries?sort=cost_eur; DROP TABLE delivery"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.sort").exists());
        mvc.perform(get("/api/deliveries?sort=customer_company"))
                .andExpect(status().isBadRequest());

        assertThat(getJson("/api/deliveries")).hasSize(5);
    }

    @Test
    void anUnknownOrderDirectionIsRejected() throws Exception {
        mvc.perform(get("/api/deliveries?order=sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.order").exists());
    }

    @Test
    void anUnparseableDateOrNumberIsAValidationError() throws Exception {
        mvc.perform(get("/api/deliveries?shipping_date_from=last-tuesday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.shipping_date_from").exists());
        mvc.perform(get("/api/deliveries?cost_min=cheap"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cost_min").exists());
        mvc.perform(get("/api/deliveries?transport_method=TELEPORT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.transport_method").exists());
    }

    @Test
    void blankFilterValuesAreIgnored() throws Exception {
        assertThat(getJson("/api/deliveries?customer_company=&origin_city=")).hasSize(5);
    }

    @Test
    void eachRowCarriesItsStatus_F11() throws Exception {
        assertThat(field(getJson("/api/deliveries"), "status")).containsExactly(
                "OFFERS_RECEIVED", "ON_THE_WAY", "OFFER_ACCEPTED", "DELIVERED", "DELIVERED");
    }

    @Test
    void filtersByOneStatus_F12() throws Exception {
        assertThat(shippingDates(getJson("/api/deliveries?status=DELIVERED")))
                .containsExactly("2026-03-15", "2026-01-09");
        assertThat(shippingDates(getJson("/api/deliveries?status=ON_THE_WAY")))
                .containsExactly("2026-07-02");
        assertThat(getJson("/api/deliveries?status=TO_DO")).isEmpty();
    }

    @Test
    void severalStatusesMatchAnyOfThem_F12() throws Exception {
        assertThat(shippingDates(getJson("/api/deliveries?status=ON_THE_WAY&status=DELIVERED")))
                .containsExactly("2026-07-02", "2026-03-15", "2026-01-09");
        assertThat(getJson("/api/deliveries?status=TO_DO&status=OFFERS_RECEIVED"
                + "&status=OFFER_ACCEPTED&status=ON_THE_WAY&status=DELIVERED")).hasSize(5);
    }

    @Test
    void theStatusFilterIsAndCombinedWithTheOthers_F13() throws Exception {
        assertThat(shippingDates(getJson("/api/deliveries?status=DELIVERED&status=ON_THE_WAY"
                + "&customer_company=Nordwind GmbH")))
                .containsExactly("2026-07-02");
        assertThat(shippingDates(getJson("/api/deliveries?status=DELIVERED"
                + "&transport_method=SEA")))
                .containsExactly("2026-01-09");
        assertThat(getJson("/api/deliveries?status=OFFERS_RECEIVED&destination_country=DE"))
                .isEmpty();
    }

    @Test
    void theStatusFilterKeepsTheSort_F14() throws Exception {
        assertThat(numbers(getJson("/api/deliveries?status=DELIVERED&status=OFFER_ACCEPTED"
                + "&sort=cost_eur&order=asc"), "cost_eur"))
                .containsExactly(3800.00, 4210.00, 9100.50);
    }

    @Test
    void withNoStatusTickedEveryStatusShows_F12() throws Exception {
        assertThat(getJson("/api/deliveries")).hasSize(5);
    }

    @Test
    void aBlankStatusIsIgnored_F12() throws Exception {
        assertThat(getJson("/api/deliveries?status=")).hasSize(5);
        assertThat(shippingDates(getJson("/api/deliveries?status=&status=DELIVERED")))
                .containsExactly("2026-03-15", "2026-01-09");
    }

    @Test
    void anUnknownStatusIsAValidationError_F12() throws Exception {
        mvc.perform(get("/api/deliveries?status=LOST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.status").exists());
        mvc.perform(get("/api/deliveries?status=DELIVERED&status=delivered"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.status").exists());
    }

    @Test
    void aCostBoundLeavesOutDeliveriesWithoutARealPrice_F12() throws Exception {
        createDelivery(bodyWith("status", "TO_DO", "cost_eur", null));

        assertThat(getJson("/api/deliveries")).hasSize(6);
        assertThat(getJson("/api/deliveries?cost_min=0")).hasSize(5);
        assertThat(getJson("/api/deliveries?cost_max=100000")).hasSize(5);
        assertThat(getJson("/api/deliveries?cost_min=0&cost_max=100000")).hasSize(5);
    }

    @Test
    void aMissingCostSortsLastInEitherOrder_F11() throws Exception {
        createDelivery(bodyWith("status", "TO_DO", "cost_eur", null));

        assertThat(numbers(getJson("/api/deliveries?sort=cost_eur&order=asc"), "cost_eur"))
                .containsExactly(1250.75, 2600.00, 3800.00, 4210.00, 9100.50, null);
        assertThat(numbers(getJson("/api/deliveries?sort=cost_eur&order=desc"), "cost_eur"))
                .containsExactly(9100.50, 4210.00, 3800.00, 2600.00, 1250.75, null);
    }

    @Test
    void aMissingShippingDateSortsLastInEitherOrder_F11() throws Exception {
        createDelivery(bodyWith("status", "TO_DO", "shipping_date", null, "arrival_date", null));

        assertThat(shippingDates(getJson("/api/deliveries"))).containsExactly(
                "2026-09-30", "2026-07-02", "2026-05-01", "2026-03-15", "2026-01-09", null);
        assertThat(shippingDates(getJson("/api/deliveries?sort=shipping_date&order=asc")))
                .containsExactly(
                        "2026-01-09", "2026-03-15", "2026-05-01", "2026-07-02", "2026-09-30", null);
    }

    @Test
    void aMissingArrivalDateSortsLastInEitherOrder_F11() throws Exception {
        assertThat(field(getJson("/api/deliveries?sort=arrival_date&order=asc"), "arrival_date"))
                .containsExactly("2026-02-20", "2026-03-20", "2026-07-19", "2026-10-25", null);
        assertThat(field(getJson("/api/deliveries?sort=arrival_date&order=desc"), "arrival_date"))
                .containsExactly("2026-10-25", "2026-07-19", "2026-03-20", "2026-02-20", null);
    }

    @Test
    void aMissingVolumeOrWeightSortsLastInEitherOrder_F11() throws Exception {
        createDelivery(bodyWith("status", "TO_DO", "volume_m3", null, "gross_weight_kg", null));

        assertThat(numbers(getJson("/api/deliveries?sort=volume_m3&order=desc"), "volume_m3"))
                .containsExactly(20.00, 12.60, 8.40, 2.10, 1.00, null);
        assertThat(numbers(getJson("/api/deliveries?sort=gross_weight_kg&order=asc"),
                "gross_weight_kg")).containsExactly(150.0, 410.0, 1900.0, 2480.0, 5000.0, null);
    }

    @Test
    void theArrivalRangeUsesTheRealArrivalNotTheExpectedOne_S10Criterion17() throws Exception {
        // Expected in range, real outside: left out. Expected outside, real in range: listed.
        createDelivery(bodyWith("customer_company", "Kestrel Imports", "status", "DELIVERED",
                "expected_arrival_date", "2026-12-10", "arrival_date", "2027-01-05"));
        createDelivery(bodyWith("customer_company", "Kestrel Imports", "status", "DELIVERED",
                "expected_arrival_date", "2027-01-02", "arrival_date", "2026-12-20"));
        createDelivery(bodyWith("customer_company", "Kestrel Imports", "status", "ON_THE_WAY",
                "expected_arrival_date", "2026-12-15", "arrival_date", null));

        JsonNode rows = getJson("/api/deliveries?customer_company=Kestrel Imports"
                + "&arrival_date_from=2026-12-01&arrival_date_to=2026-12-31");

        assertThat(field(rows, "arrival_date")).containsExactly("2026-12-20");
    }

    @Test
    void theArrivalSortUsesTheRealArrivalAndPutsAnExpectedOnlyDeliveryLast_S10Criterion17()
            throws Exception {
        createDelivery(bodyWith("customer_company", "Kestrel Imports", "status", "DELIVERED",
                "expected_arrival_date", "2026-01-01", "arrival_date", "2026-04-02"));
        createDelivery(bodyWith("customer_company", "Kestrel Imports", "status", "DELIVERED",
                "expected_arrival_date", "2026-09-01", "arrival_date", "2026-03-05"));
        createDelivery(bodyWith("customer_company", "Kestrel Imports", "status", "ON_THE_WAY",
                "expected_arrival_date", "2026-02-01", "arrival_date", null));

        assertThat(field(getJson("/api/deliveries?customer_company=Kestrel Imports"
                + "&sort=arrival_date&order=asc"), "arrival_date"))
                .containsExactly("2026-03-05", "2026-04-02", null);
        assertThat(field(getJson("/api/deliveries?customer_company=Kestrel Imports"
                + "&sort=arrival_date&order=desc"), "arrival_date"))
                .containsExactly("2026-04-02", "2026-03-05", null);
    }

    private void seedDelivery(String company, String originCountry, String originCity,
            String destCountry, String destCity, String method, String status, String shipping,
            String arrival, double cost, double volume, double gross, double pallets)
            throws Exception {
        createDelivery(bodyWith(
                "customer_company", company,
                "origin_country", originCountry,
                "origin_city", originCity,
                "destination_country", destCountry,
                "destination_city", destCity,
                "transport_method", method,
                "status", status,
                "shipping_date", shipping,
                "arrival_date", arrival,
                "cost_eur", cost,
                "volume_m3", volume,
                "gross_weight_kg", gross,
                "pallet_count", pallets));
    }

    private static List<String> shippingDates(JsonNode rows) {
        return field(rows, "shipping_date");
    }

    private static List<String> field(JsonNode rows, String name) {
        List<String> values = new ArrayList<>();
        rows.forEach(row -> values.add(row.get(name).isNull() ? null : row.get(name).asText()));
        return values;
    }

    private static List<Double> numbers(JsonNode rows, String name) {
        List<Double> values = new ArrayList<>();
        rows.forEach(row -> values.add(row.get(name).isNull() ? null : row.get(name).asDouble()));
        return values;
    }
}
