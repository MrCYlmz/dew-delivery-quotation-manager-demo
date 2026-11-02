package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.dew.backend.domain.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** F-29: a date left out or null keeps the stored one, so a move never clears a date. */
public record StatusMoveRequest(
        @JsonProperty("status") @NotNull(message = "required") DeliveryStatus status,
        @JsonProperty("shipping_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate shippingDate,
        @JsonProperty("expected_arrival_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate expectedArrivalDate,
        @JsonProperty("arrival_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrivalDate) {
}
