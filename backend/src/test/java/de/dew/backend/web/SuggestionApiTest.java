package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuggestionApiTest extends BackendTest {

    private void seed() throws Exception {
        createDelivery(bodyWith("customer_company", "Nordwind GmbH",
                "origin_country", "CN", "origin_city", "Ningbo",
                "destination_country", "DE", "destination_city", "Munich"));
        createDelivery(bodyWith("customer_company", "Example Interiors",
                "origin_country", "CN", "origin_city", "Shanghai",
                "destination_country", "DE", "destination_city", "Hamburg"));
        createDelivery(bodyWith("customer_company", "Example Interiors",
                "origin_country", "VN", "origin_city", "Haiphong",
                "destination_country", "NL", "destination_city", "Rotterdam"));
    }

    @Test
    void companiesAreDistinctAndSorted() throws Exception {
        seed();
        assertThat(values(getJson("/api/suggestions/companies")))
                .containsExactly("Example Interiors", "Nordwind GmbH");
    }

    @Test
    void countriesAreTheIsoCodesAlreadyInTheDataPerField() throws Exception {
        seed();
        assertThat(values(getJson("/api/suggestions/countries?field=origin")))
                .containsExactly("CN", "VN");
        assertThat(values(getJson("/api/suggestions/countries?field=destination")))
                .containsExactly("DE", "NL");
    }

    @Test
    void citiesAreScopedToTheFieldAndOptionallyToACountry() throws Exception {
        seed();
        assertThat(values(getJson("/api/suggestions/cities?field=origin&country=CN")))
                .containsExactly("Ningbo", "Shanghai");
        assertThat(values(getJson("/api/suggestions/cities?field=origin&country=VN")))
                .containsExactly("Haiphong");
        assertThat(values(getJson("/api/suggestions/cities?field=destination&country=DE")))
                .containsExactly("Hamburg", "Munich");
    }

    @Test
    void citiesWithoutACountryReturnsEveryCityForThatField() throws Exception {
        seed();
        assertThat(values(getJson("/api/suggestions/cities?field=origin")))
                .containsExactly("Haiphong", "Ningbo", "Shanghai");
        assertThat(values(getJson("/api/suggestions/cities?field=destination")))
                .containsExactly("Hamburg", "Munich", "Rotterdam");
        assertThat(values(getJson("/api/suggestions/cities?field=origin&country=")))
                .containsExactly("Haiphong", "Ningbo", "Shanghai");
    }

    @Test
    void anUnknownCountryNarrowsToNothingRatherThanEverything() throws Exception {
        seed();
        assertThat(getJson("/api/suggestions/cities?field=origin&country=ZZ")).isEmpty();
    }

    @Test
    void valuesAreReturnedExactlyAsStored() throws Exception {
        createDelivery(bodyWith("customer_company", "Example Interiors"));
        createDelivery(bodyWith("customer_company", "Example interiors"));

        List<String> companies = values(getJson("/api/suggestions/companies"));
        assertThat(companies).containsExactly("Example Interiors", "Example interiors");
        for (String company : companies) {
            assertThat(getJson("/api/deliveries?customer_company={c}", company)).hasSize(1);
        }
    }

    @Test
    void aTrailingSpaceDoesNotCreateASecondSuggestion() throws Exception {
        createDelivery(bodyWith("customer_company", "Example Interiors"));
        createDelivery(bodyWith("customer_company", "  Example Interiors "));

        assertThat(values(getJson("/api/suggestions/companies")))
                .containsExactly("Example Interiors");
        assertThat(getJson("/api/deliveries?customer_company=Example Interiors")).hasSize(2);
    }

    @Test
    void blankAndAbsentValuesAreExcluded() throws Exception {
        createDelivery(bodyWith("origin_city", "Ningbo", "origin_postal_code", "   "));
        assertThat(values(getJson("/api/suggestions/cities?field=origin")))
                .containsExactly("Ningbo");
    }

    @Test
    void anEmptyDatabaseYieldsEmptyArraysNotErrors() throws Exception {
        assertThat(getJson("/api/suggestions/companies")).isEmpty();
        assertThat(getJson("/api/suggestions/countries?field=origin")).isEmpty();
        assertThat(getJson("/api/suggestions/cities?field=destination")).isEmpty();
    }

    @Test
    void anUnknownOrMissingFieldIsAValidationError() throws Exception {
        mvc.perform(get("/api/suggestions/countries?field=middle"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.field").exists());
        mvc.perform(get("/api/suggestions/countries"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.field").value("required"));
        mvc.perform(get("/api/suggestions/cities?field=origin_city"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.field").exists());
    }

    @Test
    void aFieldNameCannotBeInjectedIntoTheQuery() throws Exception {
        seed();
        mvc.perform(get("/api/suggestions/countries?field=origin_country FROM delivery; --"))
                .andExpect(status().isBadRequest());
        assertThat(getJson("/api/deliveries")).hasSize(3);
    }

    private static List<String> values(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(node -> result.add(node.asText()));
        return result;
    }
}
