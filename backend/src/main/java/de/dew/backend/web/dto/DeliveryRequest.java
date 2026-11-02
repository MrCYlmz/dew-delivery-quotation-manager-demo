package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.dew.backend.domain.DeliveryStatus;
import de.dew.backend.domain.TransportMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryRequest(
        @JsonProperty("customer_company") @NotBlank(message = "required") String customerCompany,
        @JsonProperty("origin_country") @NotBlank(message = "required") String originCountry,
        @JsonProperty("origin_city") @NotBlank(message = "required") String originCity,
        @JsonProperty("origin_postal_code") String originPostalCode,
        @JsonProperty("origin_street") String originStreet,
        @JsonProperty("destination_country") @NotBlank(message = "required") String destinationCountry,
        @JsonProperty("destination_city") @NotBlank(message = "required") String destinationCity,
        @JsonProperty("destination_postal_code") String destinationPostalCode,
        @JsonProperty("destination_street") String destinationStreet,
        @JsonProperty("transport_method") @NotNull(message = "required") TransportMethod transportMethod,
        @JsonProperty("status") @NotNull(message = "required") DeliveryStatus status,
        @JsonProperty("shipping_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate shippingDate,
        @JsonProperty("expected_arrival_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate expectedArrivalDate,
        @JsonProperty("arrival_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrivalDate,
        @JsonProperty("offer_price_eur")
        @DecimalMin(value = "0", message = "must not be negative") BigDecimal offerPriceEur,
        @JsonProperty("cost_eur")
        @DecimalMin(value = "0", message = "must not be negative") BigDecimal costEur,
        @JsonProperty("volume_m3") BigDecimal volumeM3,
        @JsonProperty("gross_weight_kg") BigDecimal grossWeightKg,
        @JsonProperty("pallet_count") BigDecimal palletCount,
        @JsonProperty("notes") String notes) {
}
