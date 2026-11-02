package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstimateComparablesApiTest extends BackendTest {

    private static final String URL = "/api/estimate/comparables";

    @Test
    void chargeableIsTheLargerOfGrossKgAndVolumeTimesTheMethodRatio_F20() throws Exception {
        assertKg(estimate("SEA", "CN", "DE", "volume_m3=5&gross_weight_kg=900")
                .get("chargeable_kg"), "5000");
        assertKg(estimate("SEA", "CN", "DE", "volume_m3=5&gross_weight_kg=7200")
                .get("chargeable_kg"), "7200");
        assertKg(estimate("AIR", "CN", "DE", "volume_m3=2&gross_weight_kg=100")
                .get("chargeable_kg"), "334");
        assertKg(estimate("AIR", "CN", "DE", "volume_m3=2&gross_weight_kg=500")
                .get("chargeable_kg"), "500");
        assertKg(estimate("ROAD", "CN", "DE", "volume_m3=3&gross_weight_kg=500")
                .get("chargeable_kg"), "999");
        assertKg(estimate("RAIL", "CN", "DE", "volume_m3=3&gross_weight_kg=500")
                .get("chargeable_kg"), "999");
    }

    @Test
    void withOnlyVolumeChargeableIsVolumeTimesRatio_F20() throws Exception {
        assertKg(estimate("SEA", "CN", "DE", "volume_m3=7.42").get("chargeable_kg"), "7420");
        assertKg(estimate("AIR", "CN", "DE", "volume_m3=1.5").get("chargeable_kg"), "250.5");
    }

    @Test
    void withOnlyGrossWeightChargeableIsGrossWeight_F20() throws Exception {
        assertKg(estimate("SEA", "CN", "DE", "gross_weight_kg=1580").get("chargeable_kg"), "1580");
    }

    @Test
    void withNeitherVolumeNorWeightThePotentialDeliveryHasNoSize_F20() throws Exception {
        assertThat(estimate("SEA", "CN", "DE", "").get("chargeable_kg").isNull()).isTrue();
    }

    @Test
    void eachComparableGetsItsChargeableFromItsSavedTotals_F20() throws Exception {
        long both = seed("AIR", "CN", "DE", daysAgo(10), 2.0, 100.0);
        long volumeOnly = seed("AIR", "CN", "DE", daysAgo(20), 3.0, null);
        long weightOnly = seed("AIR", "CN", "DE", daysAgo(30), null, 450.0);

        JsonNode comparables = estimate("AIR", "CN", "DE", "gross_weight_kg=400").get("comparables");

        assertKg(byId(comparables, both).get("chargeable_kg"), "334");
        assertKg(byId(comparables, volumeOnly).get("chargeable_kg"), "501");
        assertKg(byId(comparables, weightOnly).get("chargeable_kg"), "450");
    }

    @Test
    void aSavedTotalOfZeroCountsAsNoSize_F20() throws Exception {
        seed("SEA", "CN", "DE", daysAgo(10), 1.0, null);
        seed("SEA", "CN", "DE", daysAgo(20), 2.0, null);
        seed("SEA", "CN", "DE", daysAgo(30), 3.0, null);
        long zeros = seed("SEA", "CN", "DE", daysAgo(5), 0.0, 0.0);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(ids(body.get("comparables"))).doesNotContain(zeros).hasSize(3);
    }

    @Test
    void withoutASizeOnTheRequestASizedComparableStillShowsItsChargeable_F20() throws Exception {
        long sized = seed("SEA", "CN", "DE", daysAgo(10), 4.8, 900.0);
        long sizeless = seed("SEA", "CN", "DE", daysAgo(20), null, null);

        JsonNode comparables = estimate("SEA", "CN", "DE", "").get("comparables");

        assertKg(byId(comparables, sized).get("chargeable_kg"), "4800");
        assertThat(byId(comparables, sizeless).get("chargeable_kg").isNull()).isTrue();
    }

    @Test
    void candidatesShareMethodOriginAndDestination_F21() throws Exception {
        long a = seed("SEA", "CN", "DE", daysAgo(10), 1.0, null);
        long b = seed("SEA", "CN", "DE", daysAgo(20), 1.0, null);
        long c = seed("SEA", "CN", "DE", daysAgo(30), 1.0, null);
        seed("AIR", "CN", "DE", daysAgo(1), 1.0, null);
        seed("SEA", "VN", "DE", daysAgo(1), 1.0, null);
        seed("SEA", "CN", "PL", daysAgo(1), 1.0, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("fallback").asText()).isEqualTo("NONE");
        assertThat(ids(body.get("comparables"))).containsExactly(a, b, c);
    }

    @Test
    void withASizedRequestCandidatesWithoutASizeAreLeftOut_F21() throws Exception {
        long a = seed("SEA", "CN", "DE", daysAgo(10), 1.0, null);
        long b = seed("SEA", "CN", "DE", daysAgo(20), null, 800.0);
        long c = seed("SEA", "CN", "DE", daysAgo(30), 1.0, 500.0);
        long sizeless = seed("SEA", "CN", "DE", daysAgo(1), null, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(ids(body.get("comparables"))).containsExactlyInAnyOrder(a, b, c)
                .doesNotContain(sizeless);
    }

    @Test
    void withoutASizeEveryCandidateCountsAndAgeAloneRanks_F21() throws Exception {
        long oldClose = seed("SEA", "CN", "DE", daysAgo(400), 1.0, null);
        long newFar = seed("SEA", "CN", "DE", daysAgo(5), 40.0, null);
        long sizeless = seed("SEA", "CN", "DE", daysAgo(100), null, null);

        JsonNode body = estimate("SEA", "CN", "DE", "");

        assertThat(body.get("fallback").asText()).isEqualTo("NONE");
        assertThat(ids(body.get("comparables"))).containsExactly(newFar, sizeless, oldClose);
    }

    @Test
    void rankingCombinesLogSizeClosenessAndHalfTheAgeInYears_F21() throws Exception {
        // request chargeable 1000; scores: sameSizeTwoYears 0.999, doubleSizeRecent 0.707,
        // sameSizeHalfYear 0.246, quarterSizeToday 1.386
        long sameSizeTwoYears = seed("SEA", "CN", "DE", daysAgo(730), 1.0, null);
        long doubleSizeRecent = seed("SEA", "CN", "DE", daysAgo(10), 2.0, null);
        long sameSizeHalfYear = seed("SEA", "CN", "DE", daysAgo(180), 1.0, null);
        long quarterSizeToday = seed("SEA", "CN", "DE", daysAgo(0), 0.25, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(ids(body.get("comparables")))
                .containsExactly(sameSizeHalfYear, doubleSizeRecent, sameSizeTwoYears, quarterSizeToday);
    }

    @Test
    void sizeClosenessIsARatioNotADifference_F21() throws Exception {
        // against 1000: 500 is |ln 0.5| = 0.693 away, 1800 is |ln 1.8| = 0.588
        long half = seed("SEA", "CN", "DE", daysAgo(30), 0.5, null);
        long larger = seed("SEA", "CN", "DE", daysAgo(30), 1.8, null);
        long same = seed("SEA", "CN", "DE", daysAgo(30), 1.0, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(ids(body.get("comparables"))).containsExactly(same, larger, half);
    }

    @Test
    void rankingUsesChargeableNotRawWeight_F21() throws Exception {
        // AIR request: max(1000, 1 x 167) = 1000. bulky: max(100, 6 x 167) = 1002; dense: 300
        long bulky = seed("AIR", "CN", "DE", daysAgo(30), 6.0, 100.0);
        long dense = seed("AIR", "CN", "DE", daysAgo(30), 1.0, 300.0);
        long third = seed("AIR", "CN", "DE", daysAgo(30), null, 5000.0);

        JsonNode body = estimate("AIR", "CN", "DE", "volume_m3=1&gross_weight_kg=1000");

        assertThat(ids(body.get("comparables"))).containsExactly(bulky, dense, third);
    }

    @Test
    void equalScoresGoToTheHigherIdFirst_F21() throws Exception {
        LocalDate shipped = daysAgo(60);
        long first = seed("SEA", "CN", "DE", shipped, 2.0, null);
        long second = seed("SEA", "CN", "DE", shipped, 2.0, null);
        long third = seed("SEA", "CN", "DE", shipped, 2.0, null);

        assertThat(ids(estimate("SEA", "CN", "DE", "volume_m3=1").get("comparables")))
                .containsExactly(third, second, first);
        assertThat(ids(estimate("SEA", "CN", "DE", "").get("comparables")))
                .containsExactly(third, second, first);
    }

    @Test
    void equalScoresGoToTheNewerShippingDateBeforeTheHigherId_F21() throws Exception {
        long newerFuture = seed("SEA", "CN", "DE", TODAY.plusDays(60), 1.0, null);
        long nearerFuture = seed("SEA", "CN", "DE", TODAY.plusDays(20), 1.0, null);
        long past = seed("SEA", "CN", "DE", daysAgo(100), 1.0, null);

        assertThat(ids(estimate("SEA", "CN", "DE", "volume_m3=1").get("comparables")))
                .containsExactly(newerFuture, nearerFuture, past);
        assertThat(ids(estimate("SEA", "CN", "DE", "").get("comparables")))
                .containsExactly(newerFuture, nearerFuture, past);
    }

    @Test
    void theTopTenAreKept_F21() throws Exception {
        List<Long> seeded = new ArrayList<>();
        for (int days = 1; days <= 12; days++) {
            seeded.add(seed("SEA", "CN", "DE", daysAgo(days * 30), 1.0, null));
        }

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(ids(body.get("comparables"))).containsExactlyElementsOf(seeded.subList(0, 10));
    }

    @Test
    void withThreeOnTheRouteNoFallbackIsUsedEvenIfOtherRoutesAreCloser_F21() throws Exception {
        seed("SEA", "CN", "DE", daysAgo(700), 9.0, null);
        seed("SEA", "CN", "DE", daysAgo(800), 9.0, null);
        seed("SEA", "CN", "DE", daysAgo(900), 9.0, null);
        long otherDestination = seed("SEA", "CN", "PL", daysAgo(1), 1.0, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("fallback").asText()).isEqualTo("NONE");
        assertThat(ids(body.get("comparables"))).hasSize(3).doesNotContain(otherDestination);
    }

    @Test
    void withFewerThanThreeTheDestinationIsIgnored_F21() throws Exception {
        long route1 = seed("SEA", "CN", "DE", daysAgo(10), 1.0, null);
        long route2 = seed("SEA", "CN", "DE", daysAgo(20), 1.0, null);
        long poland = seed("SEA", "CN", "PL", daysAgo(30), 1.0, null);
        long italy = seed("SEA", "CN", "IT", daysAgo(40), 1.0, null);
        long otherOrigin = seed("SEA", "VN", "DE", daysAgo(5), 1.0, null);
        long otherMethod = seed("AIR", "CN", "DE", daysAgo(5), 1.0, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("fallback").asText()).isEqualTo("DESTINATION_IGNORED");
        assertThat(ids(body.get("comparables"))).containsExactly(route1, route2, poland, italy)
                .doesNotContain(otherOrigin, otherMethod);
    }

    @Test
    void theFallbackCountsCandidatesAfterTheSizeFilter_F21() throws Exception {
        seed("SEA", "CN", "DE", daysAgo(10), 1.0, null);
        seed("SEA", "CN", "DE", daysAgo(20), 1.0, null);
        long sizeless = seed("SEA", "CN", "DE", daysAgo(30), null, null);
        long poland = seed("SEA", "CN", "PL", daysAgo(40), 1.0, null);

        JsonNode sized = estimate("SEA", "CN", "DE", "volume_m3=1");
        assertThat(sized.get("fallback").asText()).isEqualTo("DESTINATION_IGNORED");
        assertThat(ids(sized.get("comparables"))).contains(poland).doesNotContain(sizeless);

        JsonNode unsized = estimate("SEA", "CN", "DE", "");
        assertThat(unsized.get("fallback").asText()).isEqualTo("NONE");
        assertThat(ids(unsized.get("comparables"))).contains(sizeless).doesNotContain(poland);
    }

    @Test
    void stillFewerThanThreeAlsoIgnoresTheOriginKeepingTheMethod_F21() throws Exception {
        long route = seed("SEA", "CN", "DE", daysAgo(10), 1.0, null);
        long elsewhere1 = seed("SEA", "VN", "NL", daysAgo(20), 1.0, null);
        long elsewhere2 = seed("SEA", "TR", "US", daysAgo(30), 1.0, null);
        long otherMethod1 = seed("AIR", "CN", "DE", daysAgo(1), 1.0, null);
        long otherMethod2 = seed("ROAD", "CN", "DE", daysAgo(1), 1.0, null);
        long otherMethod3 = seed("RAIL", "CN", "DE", daysAgo(1), 1.0, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("fallback").asText()).isEqualTo("ORIGIN_AND_DESTINATION_IGNORED");
        assertThat(ids(body.get("comparables"))).containsExactly(route, elsewhere1, elsewhere2)
                .doesNotContain(otherMethod1, otherMethod2, otherMethod3);
    }

    @Test
    void noCandidateAtAllGivesAnEmptyListAfterIgnoringOriginAndDestination_F21() throws Exception {
        seed("AIR", "CN", "DE", daysAgo(10), 1.0, null);
        seed("SEA", "CN", "DE", daysAgo(10), null, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("comparables")).isEmpty();
        assertThat(body.get("fallback").asText()).isEqualTo("ORIGIN_AND_DESTINATION_IGNORED");
        assertThat(body.get("removed_company_names").asInt()).isZero();
        assertKg(body.get("chargeable_kg"), "1000");
    }

    @Test
    void everyCompanyNameInTheArchiveIsReplacedInNotesIgnoringCase_F22() throws Exception {
        seed("AIR", "VN", "NL", daysAgo(10), 1.0, null, "A+B (HK) Ltd.", null);
        seed("SEA", "CN", "DE", daysAgo(10), 1.0, null, "Example Interiors",
                "example interiors wanted it fast; NORDWIND GMBH paid customs");
        seed("SEA", "CN", "DE", daysAgo(20), 1.0, null, "Nordwind GmbH",
                "split with a+b (hk) ltd., rush premium");
        seed("SEA", "CN", "DE", daysAgo(30), 1.0, null, "Nordwind GmbH", null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");
        JsonNode comparables = body.get("comparables");

        assertThat(comparables.get(0).get("notes").asText())
                .isEqualTo("[customer] wanted it fast; [customer] paid customs");
        assertThat(comparables.get(1).get("notes").asText())
                .isEqualTo("split with [customer], rush premium");
        assertThat(comparables.get(2).get("notes").isNull()).isTrue();
        assertThat(body.get("removed_company_names").asInt()).isEqualTo(3);
    }

    @Test
    void namesAreReplacedOnlyAsWholeWords_F22() throws Exception {
        seed("SEA", "CN", "DE", daysAgo(10), 1.0, null, "Kestrel",
                "Kestrels nest, kestrel-paid, ShopKestrel and Kestrel");
        seed("SEA", "CN", "DE", daysAgo(20), 1.0, null, "Kestrel", null);
        seed("SEA", "CN", "DE", daysAgo(30), 1.0, null, "Kestrel", null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("comparables").get(0).get("notes").asText())
                .isEqualTo("Kestrels nest, [customer]-paid, ShopKestrel and [customer]");
        assertThat(body.get("removed_company_names").asInt()).isEqualTo(2);
    }

    @Test
    void theLongestNameIsReplacedFirstAndAnOverlappingShorterOneIsNotCountedAgain_F22()
            throws Exception {
        seed("SEA", "CN", "DE", daysAgo(10), 1.0, null, "Example",
                "example interiors booked it, Example paid");
        seed("SEA", "CN", "DE", daysAgo(20), 1.0, null, "Example Interiors", null);
        seed("SEA", "CN", "DE", daysAgo(30), 1.0, null, "Interiors", null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("comparables").get(0).get("notes").asText())
                .isEqualTo("[customer] booked it, [customer] paid");
        assertThat(body.get("removed_company_names").asInt()).isEqualTo(2);
    }

    @Test
    void theCustomerCompanyIsNeverInTheResponse_F22() throws Exception {
        seed("SEA", "CN", "DE", daysAgo(10), 1.0, null, "Example Interiors", "Example Interiors asked twice");
        seed("SEA", "CN", "DE", daysAgo(20), 1.0, null, "Nordwind GmbH", "plain note");
        seed("SEA", "CN", "DE", daysAgo(30), 1.0, null, "Kestrel Imports", null);

        String response = mvc.perform(get(URL + "?transport_method=SEA&origin_country=CN"
                        + "&destination_country=DE&volume_m3=1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("customer_company")
                .doesNotContain("Example Interiors")
                .doesNotContain("Nordwind GmbH")
                .doesNotContain("Kestrel Imports");
    }

    @Test
    void theReplacementCountCoversOnlyTheReturnedNotes_F22() throws Exception {
        for (int i = 1; i <= 10; i++) {
            seed("SEA", "CN", "DE", daysAgo(i * 10), 1.0, null, "Nordwind GmbH",
                    i == 1 ? "Nordwind GmbH again" : null);
        }
        seed("SEA", "CN", "DE", daysAgo(2000), 1.0, null, "Example Interiors",
                "Example Interiors and Nordwind GmbH, both");

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("comparables")).hasSize(10);
        assertThat(body.get("removed_company_names").asInt()).isEqualTo(1);
    }

    @Test
    void theStoredNotesAreLeftUnchanged_F22() throws Exception {
        long id = seed("SEA", "CN", "DE", daysAgo(10), 1.0, null, "Example Interiors",
                "Example Interiors asked twice");
        int before = getJson("/api/deliveries").size();

        estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(getJson("/api/deliveries/{id}", id).get("notes").asText())
                .isEqualTo("Example Interiors asked twice");
        assertThat(getJson("/api/deliveries")).hasSize(before);
    }

    @Test
    void aComparableCarriesItsRecordFieldsAndAgeInWholeMonths() throws Exception {
        LocalDate shipped = TODAY.minusMonths(7);
        long id = createDelivery(bodyWith(
                "origin_country", "CN", "origin_city", "Ningbo",
                "destination_country", "DE", "destination_city", "Munich",
                "transport_method", "SEA",
                "shipping_date", shipped.toString(),
                "expected_arrival_date", shipped.plusDays(39).toString(),
                "arrival_date", shipped.plusDays(42).toString(),
                "cost_eur", 4100.00,
                "volume_m3", 4.8, "gross_weight_kg", 900, "pallet_count", 6,
                "notes", "rush premium"));

        JsonNode comparable = byId(estimate("SEA", "CN", "DE", "").get("comparables"), id);

        assertThat(comparable.get("origin_country").asText()).isEqualTo("CN");
        assertThat(comparable.get("origin_city").asText()).isEqualTo("Ningbo");
        assertThat(comparable.get("destination_country").asText()).isEqualTo("DE");
        assertThat(comparable.get("destination_city").asText()).isEqualTo("Munich");
        assertThat(comparable.get("transport_method").asText()).isEqualTo("SEA");
        assertThat(comparable.get("shipping_date").asText()).isEqualTo(shipped.toString());
        assertThat(comparable.get("expected_arrival_date").asText())
                .isEqualTo(shipped.plusDays(39).toString());
        assertThat(comparable.get("arrival_date").asText()).isEqualTo(shipped.plusDays(42).toString());
        assertThat(comparable.get("age_months").asInt()).isEqualTo(7);
        assertThat(comparable.get("volume_m3").decimalValue()).isEqualByComparingTo("4.8");
        assertThat(comparable.get("gross_weight_kg").decimalValue()).isEqualByComparingTo("900");
        assertThat(comparable.get("pallet_count").decimalValue()).isEqualByComparingTo("6");
        assertThat(comparable.get("cost_eur").decimalValue()).isEqualByComparingTo("4100.00");
        assertThat(comparable.get("notes").asText()).isEqualTo("rush premium");
    }

    @Test
    void ageInMonthsIsRoundedDown() throws Exception {
        long sevenMonths = seed("SEA", "CN", "DE", TODAY.minusMonths(7), 1.0, null);
        long almostSeven = seed("SEA", "CN", "DE", TODAY.minusMonths(7).plusDays(1), 1.0, null);
        long shippedToday = seed("SEA", "CN", "DE", TODAY, 1.0, null);

        JsonNode comparables = estimate("SEA", "CN", "DE", "").get("comparables");

        assertThat(byId(comparables, sevenMonths).get("age_months").asInt()).isEqualTo(7);
        assertThat(byId(comparables, almostSeven).get("age_months").asInt()).isEqualTo(6);
        assertThat(byId(comparables, shippedToday).get("age_months").asInt()).isZero();
    }

    @Test
    void aShippingDateAfterTodayHasAgeZeroMonths_F21() throws Exception {
        long future = seed("SEA", "CN", "DE", TODAY.plusMonths(3), 1.0, null);

        JsonNode comparable = byId(estimate("SEA", "CN", "DE", "").get("comparables"), future);

        assertThat(comparable.get("age_months").asInt()).isZero();
    }

    @Test
    void aShippingDateAfterTodayScoresAsAgeZeroNotBelowZero_F21() throws Exception {
        // against 1000: futureDouble scores |ln 2| = 0.693 (0.193 if a year ahead counted as -1),
        // sameSizeOlder 0.5 x 200 / 365.25 = 0.274
        long futureDouble = seed("SEA", "CN", "DE", daysAgo(-365), 2.0, null);
        long sameSizeOlder = seed("SEA", "CN", "DE", daysAgo(200), 1.0, null);
        long third = seed("SEA", "CN", "DE", daysAgo(10), 4.0, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(ids(body.get("comparables"))).containsExactly(sameSizeOlder, futureDouble, third);
    }

    @Test
    void aMissingArrivalDateIsNull() throws Exception {
        long id = seed("SEA", "CN", "DE", daysAgo(10), 1.0, null);

        JsonNode comparable = byId(estimate("SEA", "CN", "DE", "").get("comparables"), id);

        assertThat(comparable.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void aComparableWithOnlyAnExpectedArrivalCarriesItWithANullRealArrival_F22() throws Exception {
        long id = createDelivery(bodyWith("status", "ON_THE_WAY",
                "shipping_date", daysAgo(10).toString(),
                "expected_arrival_date", daysAgo(-20).toString(), "arrival_date", null));

        JsonNode comparable = byId(estimate("SEA", "CN", "DE", "").get("comparables"), id);

        assertThat(comparable.get("expected_arrival_date").asText())
                .isEqualTo(daysAgo(-20).toString());
        assertThat(comparable.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void aComparableWithoutEitherArrivalCarriesTwoNulls_F22() throws Exception {
        long id = seed("SEA", "CN", "DE", daysAgo(10), 1.0, null);

        JsonNode comparable = byId(estimate("SEA", "CN", "DE", "").get("comparables"), id);

        assertThat(comparable.get("expected_arrival_date").isNull()).isTrue();
        assertThat(comparable.get("arrival_date").isNull()).isTrue();
    }

    @Test
    void comparablesComeFromEveryStatusExceptToDo_F21() throws Exception {
        long toDo = seedPriced("TO_DO", daysAgo(1), 4100.0, 3900.0);
        long offersReceived = seedPriced("OFFERS_RECEIVED", daysAgo(10), 4100.0, 3900.0);
        long offerAccepted = seedPriced("OFFER_ACCEPTED", daysAgo(20), 4100.0, 3900.0);
        long onTheWay = seedPriced("ON_THE_WAY", daysAgo(30), 4100.0, 3900.0);
        long delivered = seedPriced("DELIVERED", daysAgo(40), 4100.0, 3900.0);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("fallback").asText()).isEqualTo("NONE");
        assertThat(ids(body.get("comparables")))
                .containsExactly(offersReceived, offerAccepted, onTheWay, delivered)
                .doesNotContain(toDo);
    }

    @Test
    void aCandidateWithNeitherPriceIsLeftOut_F21() throws Exception {
        long realOnly = seedPriced("DELIVERED", daysAgo(10), 4100.0, null);
        long offerOnly = seedPriced("OFFER_ACCEPTED", daysAgo(20), null, 3900.0);
        long both = seedPriced("ON_THE_WAY", daysAgo(30), 4100.0, 3900.0);
        long neither = seedPriced("OFFERS_RECEIVED", daysAgo(5), null, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(ids(body.get("comparables"))).containsExactly(realOnly, offerOnly, both)
                .doesNotContain(neither);
    }

    @Test
    void theFallbackCountsCandidatesAfterLeavingOutToDoAndPricelessOnes_F21() throws Exception {
        seedPriced("DELIVERED", daysAgo(10), 4100.0, null);
        seedPriced("DELIVERED", daysAgo(20), 4100.0, null);
        long toDo = seedPriced("TO_DO", daysAgo(30), 4100.0, null);
        long priceless = seedPriced("OFFERS_RECEIVED", daysAgo(30), null, null);
        long poland = seed("SEA", "CN", "PL", daysAgo(40), 1.0, null);

        JsonNode body = estimate("SEA", "CN", "DE", "volume_m3=1");

        assertThat(body.get("fallback").asText()).isEqualTo("DESTINATION_IGNORED");
        assertThat(ids(body.get("comparables"))).contains(poland).doesNotContain(toDo, priceless);
    }

    @Test
    void aComparableCarriesBothPricesWhenSet_F22() throws Exception {
        long id = seedPriced("DELIVERED", daysAgo(10), 4100.0, 3900.0);

        JsonNode comparable = byId(estimate("SEA", "CN", "DE", "").get("comparables"), id);

        assertThat(comparable.get("cost_eur").decimalValue()).isEqualByComparingTo("4100.00");
        assertThat(comparable.get("offer_price_eur").decimalValue()).isEqualByComparingTo("3900.00");
    }

    @Test
    void aComparableWithOnlyAnOfferPriceCarriesItAndANullRealPrice_F22() throws Exception {
        long id = seedPriced("OFFER_ACCEPTED", daysAgo(10), null, 3900.0);

        JsonNode comparable = byId(estimate("SEA", "CN", "DE", "").get("comparables"), id);

        assertThat(comparable.get("cost_eur").isNull()).isTrue();
        assertThat(comparable.get("offer_price_eur").decimalValue()).isEqualByComparingTo("3900.00");
    }

    @Test
    void aComparableWithOnlyARealPriceCarriesANullOfferPrice_F22() throws Exception {
        long id = seedPriced("DELIVERED", daysAgo(10), 4100.0, null);

        JsonNode comparable = byId(estimate("SEA", "CN", "DE", "").get("comparables"), id);

        assertThat(comparable.get("cost_eur").decimalValue()).isEqualByComparingTo("4100.00");
        assertThat(comparable.get("offer_price_eur").isNull()).isTrue();
    }

    @Test
    void aComparableWithoutAShippingDateHasANullDateAndAgeZero_F21() throws Exception {
        long id = seedPriced("OFFER_ACCEPTED", null, null, 3900.0);

        JsonNode comparable = byId(estimate("SEA", "CN", "DE", "").get("comparables"), id);

        assertThat(comparable.get("shipping_date").isNull()).isTrue();
        assertThat(comparable.get("arrival_date").isNull()).isTrue();
        assertThat(comparable.get("age_months").asInt()).isZero();
    }

    @Test
    void aComparableWithoutAShippingDateScoresAsAgeZero_F21() throws Exception {
        // against 1000: undatedDouble scores |ln 2| = 0.693, sameSizeOlder 0.5 x 200 / 365.25
        // = 0.274, quadrupleRecent |ln 4| + 0.014 = 1.400
        long undatedDouble = seedPriced("OFFERS_RECEIVED", null, 2.0, null, 3900.0);
        long sameSizeOlder = seedPriced("DELIVERED", daysAgo(200), 1.0, 4100.0, null);
        long quadrupleRecent = seedPriced("DELIVERED", daysAgo(10), 4.0, 4100.0, null);

        assertThat(ids(estimate("SEA", "CN", "DE", "volume_m3=1").get("comparables")))
                .containsExactly(sameSizeOlder, undatedDouble, quadrupleRecent);
        assertThat(ids(estimate("SEA", "CN", "DE", "").get("comparables")))
                .containsExactly(undatedDouble, quadrupleRecent, sameSizeOlder);
    }

    @Test
    void onEqualScoresAMissingShippingDateCountsAsToday_F21() throws Exception {
        long shippedToday = seedPriced("DELIVERED", TODAY, 4100.0, null);
        long shippedLater = seedPriced("OFFER_ACCEPTED", TODAY.plusDays(20), null, 3900.0);
        long undated = seedPriced("OFFERS_RECEIVED", null, null, 3900.0);

        assertThat(ids(estimate("SEA", "CN", "DE", "").get("comparables")))
                .containsExactly(shippedLater, undated, shippedToday);
    }

    @Test
    void eachMissingRequiredParamIsAValidationError() throws Exception {
        mvc.perform(get(URL + "?origin_country=CN&destination_country=DE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.transport_method").exists());
        mvc.perform(get(URL + "?transport_method=SEA&destination_country=DE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.origin_country").exists());
        mvc.perform(get(URL + "?transport_method=SEA&origin_country=CN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.destination_country").exists());
    }

    @Test
    void eachBlankRequiredParamIsAValidationError() throws Exception {
        mvc.perform(get(URL + "?transport_method=&origin_country=CN&destination_country=DE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.transport_method").exists());
        mvc.perform(get(URL + "?transport_method=SEA&origin_country=&destination_country=DE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.origin_country").exists());
        mvc.perform(get(URL).param("transport_method", "SEA").param("origin_country", "CN")
                        .param("destination_country", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.destination_country").exists());
    }

    @Test
    void aCountryThatIsNotTwoLettersIsAValidationError() throws Exception {
        for (String country : List.of("CHN", "C", "C1", "12")) {
            mvc.perform(get(URL).param("transport_method", "SEA").param("origin_country", country)
                            .param("destination_country", "DE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation"))
                    .andExpect(jsonPath("$.fields.origin_country").exists());
            mvc.perform(get(URL).param("transport_method", "SEA").param("origin_country", "CN")
                            .param("destination_country", country))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.destination_country").exists());
        }
    }

    @Test
    void anUnknownTransportMethodIsAValidationError() throws Exception {
        mvc.perform(get(URL + "?transport_method=TELEPORT&origin_country=CN&destination_country=DE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.transport_method").exists());
    }

    @Test
    void aVolumeOrWeightOfZeroOrLessIsAValidationError() throws Exception {
        for (String value : List.of("0", "-1.5")) {
            mvc.perform(get(URL + "?transport_method=SEA&origin_country=CN&destination_country=DE"
                            + "&volume_m3=" + value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation"))
                    .andExpect(jsonPath("$.fields.volume_m3").exists());
            mvc.perform(get(URL + "?transport_method=SEA&origin_country=CN&destination_country=DE"
                            + "&gross_weight_kg=" + value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.gross_weight_kg").exists());
        }
    }

    @Test
    void aNonNumericVolumeOrWeightIsAValidationError() throws Exception {
        mvc.perform(get(URL + "?transport_method=SEA&origin_country=CN&destination_country=DE"
                        + "&volume_m3=big"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.volume_m3").exists());
        mvc.perform(get(URL + "?transport_method=SEA&origin_country=CN&destination_country=DE"
                        + "&gross_weight_kg=heavy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.gross_weight_kg").exists());
    }

    private JsonNode estimate(String method, String origin, String destination, String size)
            throws Exception {
        String url = URL + "?transport_method=" + method + "&origin_country=" + origin
                + "&destination_country=" + destination;
        return getJson(size.isEmpty() ? url : url + "&" + size);
    }

    private long seed(String method, String origin, String destination, LocalDate shipped,
            Double volume, Double gross) throws Exception {
        return seed(method, origin, destination, shipped, volume, gross, "Example Interiors", null);
    }

    private long seed(String method, String origin, String destination, LocalDate shipped,
            Double volume, Double gross, String company, String notes) throws Exception {
        return createDelivery(bodyWith(
                "customer_company", company,
                "transport_method", method,
                "status", "OFFER_ACCEPTED",
                "origin_country", origin,
                "destination_country", destination,
                "shipping_date", shipped.toString(),
                "arrival_date", null,
                "volume_m3", volume,
                "gross_weight_kg", gross,
                "notes", notes));
    }

    private long seedPriced(String status, LocalDate shipped, Double cost, Double offer)
            throws Exception {
        return seedPriced(status, shipped, 1.0, cost, offer);
    }

    /** SEA, CN to DE; both dates set when shipped, so ON_THE_WAY and DELIVERED are valid. */
    private long seedPriced(String status, LocalDate shipped, Double volume, Double cost,
            Double offer) throws Exception {
        return createDelivery(bodyWith(
                "status", status,
                "transport_method", "SEA",
                "origin_country", "CN",
                "destination_country", "DE",
                "shipping_date", shipped == null ? null : shipped.toString(),
                "arrival_date", shipped == null ? null : shipped.plusDays(40).toString(),
                "volume_m3", volume,
                "cost_eur", cost,
                "offer_price_eur", offer));
    }

    private static LocalDate daysAgo(int days) {
        return TODAY.minusDays(days);
    }

    private static List<Long> ids(JsonNode comparables) {
        List<Long> ids = new ArrayList<>();
        comparables.forEach(comparable -> ids.add(comparable.get("id").asLong()));
        return ids;
    }

    private static JsonNode byId(JsonNode comparables, long id) {
        for (JsonNode comparable : comparables) {
            if (comparable.get("id").asLong() == id) {
                return comparable;
            }
        }
        throw new AssertionError("comparable " + id + " not in " + comparables);
    }

    private static void assertKg(JsonNode value, String expected) {
        assertThat(value.isNumber()).as("chargeable_kg is a number").isTrue();
        assertThat(value.decimalValue()).isCloseTo(new BigDecimal(expected), within(new BigDecimal("0.01")));
    }
}
