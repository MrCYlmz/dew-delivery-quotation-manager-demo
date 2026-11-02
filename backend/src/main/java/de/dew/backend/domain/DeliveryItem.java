package de.dew.backend.domain;

import java.math.BigDecimal;

public record DeliveryItem(
        Long id,
        Long deliveryId,
        int sourceRow,
        String description,
        String articleNo,
        BigDecimal lengthMm,
        BigDecimal widthMm,
        BigDecimal heightMm,
        BigDecimal palletCount,
        BigDecimal volumeM3,
        BigDecimal grossWeightKg,
        BigDecimal netWeightKg,
        boolean volumeMismatch) {
}
