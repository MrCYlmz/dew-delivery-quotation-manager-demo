package de.dew.parser;

import java.math.BigDecimal;
import java.util.List;

public record PackingListResult(
        BigDecimal totalVolumeM3,
        BigDecimal totalGrossWeightKg,
        BigDecimal totalPalletCount,
        List<PackingListItem> items,
        int volumeMismatchCount,
        String sheetName) {
}
