package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.dew.backend.domain.Delivery;
import de.dew.backend.domain.DeliveryStatus;
import de.dew.backend.domain.TransportMethod;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BoardCardDto(
        @JsonProperty("id") long id,
        @JsonProperty("status") DeliveryStatus status,
        @JsonProperty("customer_company") String customerCompany,
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
        @JsonProperty("days_late") Integer daysLate,
        @JsonProperty("offer_price_eur") BigDecimal offerPriceEur) {

    public static BoardCardDto from(Delivery d, Integer daysLate) {
        return new BoardCardDto(d.id(), d.status(), d.customerCompany(), d.originCountry(),
                d.originCity(), d.destinationCountry(), d.destinationCity(), d.transportMethod(),
                d.shippingDate(), d.expectedArrivalDate(), d.arrivalDate(), daysLate,
                d.offerPriceEur());
    }
}
