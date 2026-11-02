package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.dew.backend.domain.DeliveryStatus;
import de.dew.backend.domain.TransportMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DeliveryDetailDto(
        @JsonProperty("id") long id,
        @JsonProperty("customer_company") String customerCompany,
        @JsonProperty("origin_country") String originCountry,
        @JsonProperty("origin_city") String originCity,
        @JsonProperty("origin_postal_code") String originPostalCode,
        @JsonProperty("origin_street") String originStreet,
        @JsonProperty("destination_country") String destinationCountry,
        @JsonProperty("destination_city") String destinationCity,
        @JsonProperty("destination_postal_code") String destinationPostalCode,
        @JsonProperty("destination_street") String destinationStreet,
        @JsonProperty("transport_method") TransportMethod transportMethod,
        @JsonProperty("status") DeliveryStatus status,
        @JsonProperty("shipping_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate shippingDate,
        @JsonProperty("expected_arrival_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate expectedArrivalDate,
        @JsonProperty("arrival_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrivalDate,
        @JsonProperty("offer_price_eur") BigDecimal offerPriceEur,
        @JsonProperty("cost_eur") BigDecimal costEur,
        @JsonProperty("volume_m3") BigDecimal volumeM3,
        @JsonProperty("gross_weight_kg") BigDecimal grossWeightKg,
        @JsonProperty("pallet_count") BigDecimal palletCount,
        @JsonProperty("notes") String notes,
        @JsonProperty("has_notes") boolean hasNotes,
        @JsonProperty("excel_filename") String excelFilename,
        @JsonProperty("moved_to_delivered_on")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate movedToDeliveredOn,
        @JsonProperty("totals_edited") TotalsEdited totalsEdited,
        @JsonProperty("item_sums") Totals itemSums,
        @JsonProperty("items") List<ItemDto> items,
        @JsonProperty("pdfs") List<PdfDto> pdfs) {

    public record TotalsEdited(
            @JsonProperty("volume_m3") boolean volumeM3,
            @JsonProperty("gross_weight_kg") boolean grossWeightKg,
            @JsonProperty("pallet_count") boolean palletCount) {
    }

    public record Totals(
            @JsonProperty("volume_m3") BigDecimal volumeM3,
            @JsonProperty("gross_weight_kg") BigDecimal grossWeightKg,
            @JsonProperty("pallet_count") BigDecimal palletCount) {
    }
}
