package de.dew.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliverySummary(
        long id,
        String customerCompany,
        String originCountry,
        String originCity,
        String destinationCountry,
        String destinationCity,
        TransportMethod transportMethod,
        DeliveryStatus status,
        LocalDate shippingDate,
        LocalDate arrivalDate,
        BigDecimal costEur,
        BigDecimal volumeM3,
        BigDecimal grossWeightKg,
        BigDecimal palletCount,
        boolean hasNotes,
        int pdfCount) {
}
