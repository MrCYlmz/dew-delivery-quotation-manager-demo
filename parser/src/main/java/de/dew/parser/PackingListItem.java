package de.dew.parser;

import java.math.BigDecimal;

public record PackingListItem(
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
