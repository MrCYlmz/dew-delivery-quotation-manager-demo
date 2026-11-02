package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.dew.backend.domain.ComparablesFallback;
import de.dew.backend.domain.Delivery;
import de.dew.backend.domain.TransportMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EstimateComparablesDto(
        @JsonProperty("chargeable_kg") BigDecimal chargeableKg,
        @JsonProperty("fallback") ComparablesFallback fallback,
        @JsonProperty("removed_company_names") int removedCompanyNames,
        @JsonProperty("comparables") List<ComparableDeliveryDto> comparables) {

    public record ComparableDeliveryDto(
            @JsonProperty("id") long id,
            @JsonProperty("origin_country") String originCountry,
            @JsonProperty("origin_city") String originCity,
            @JsonProperty("destination_country") String destinationCountry,
            @JsonProperty("destination_city") String destinationCity,
            @JsonProperty("transport_method") TransportMethod transportMethod,
            @JsonProperty("shipping_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate shippingDate,
            @JsonProperty("expected_arrival_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate expectedArrivalDate,
            @JsonProperty("arrival_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrivalDate,
            @JsonProperty("age_months") int ageMonths,
            @JsonProperty("volume_m3") BigDecimal volumeM3,
            @JsonProperty("gross_weight_kg") BigDecimal grossWeightKg,
            @JsonProperty("pallet_count") BigDecimal palletCount,
            @JsonProperty("chargeable_kg") BigDecimal chargeableKg,
            @JsonProperty("cost_eur") BigDecimal costEur,
            @JsonProperty("offer_price_eur") BigDecimal offerPriceEur,
            @JsonProperty("notes") String notes) {

        /** The customer company is left out on purpose (F-22); notes arrive already masked. */
        public static ComparableDeliveryDto from(Delivery d, int ageMonths, BigDecimal chargeableKg,
                String maskedNotes) {
            return new ComparableDeliveryDto(d.id(), d.originCountry(), d.originCity(),
                    d.destinationCountry(), d.destinationCity(), d.transportMethod(),
                    d.shippingDate(), d.expectedArrivalDate(), d.arrivalDate(), ageMonths,
                    d.volumeM3(), d.grossWeightKg(), d.palletCount(), chargeableKg, d.costEur(),
                    d.offerPriceEur(), maskedNotes);
        }
    }
}
