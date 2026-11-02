package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.dew.backend.domain.DeliveryStatus;
import de.dew.backend.domain.DeliverySummary;
import de.dew.backend.domain.TransportMethod;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryListRowDto(
        @JsonProperty("id") long id,
        @JsonProperty("customer_company") String customerCompany,
        @JsonProperty("origin_country") String originCountry,
        @JsonProperty("origin_city") String originCity,
        @JsonProperty("destination_country") String destinationCountry,
        @JsonProperty("destination_city") String destinationCity,
        @JsonProperty("transport_method") TransportMethod transportMethod,
        @JsonProperty("status") DeliveryStatus status,
        @JsonProperty("shipping_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate shippingDate,
        @JsonProperty("arrival_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrivalDate,
        @JsonProperty("cost_eur") BigDecimal costEur,
        @JsonProperty("volume_m3") BigDecimal volumeM3,
        @JsonProperty("gross_weight_kg") BigDecimal grossWeightKg,
        @JsonProperty("pallet_count") BigDecimal palletCount,
        @JsonProperty("has_notes") boolean hasNotes,
        @JsonProperty("pdf_count") int pdfCount) {

    public static DeliveryListRowDto from(DeliverySummary s) {
        return new DeliveryListRowDto(s.id(), s.customerCompany(), s.originCountry(), s.originCity(),
                s.destinationCountry(), s.destinationCity(), s.transportMethod(), s.status(),
                s.shippingDate(), s.arrivalDate(), s.costEur(), s.volumeM3(), s.grossWeightKg(),
                s.palletCount(), s.hasNotes(), s.pdfCount());
    }
}
