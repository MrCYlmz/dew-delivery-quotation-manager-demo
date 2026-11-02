package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dew.parser.PackingListResult;
import java.math.BigDecimal;
import java.util.List;

public record ParseResultDto(
        @JsonProperty("total_volume_m3") BigDecimal totalVolumeM3,
        @JsonProperty("total_gross_weight_kg") BigDecimal totalGrossWeightKg,
        @JsonProperty("total_pallet_count") BigDecimal totalPalletCount,
        @JsonProperty("items") List<ItemDto> items,
        @JsonProperty("volume_mismatch_count") int volumeMismatchCount,
        @JsonProperty("sheet_name") String sheetName) {

    public static ParseResultDto from(PackingListResult result) {
        List<ItemDto> items = result.items() == null
                ? List.of()
                : result.items().stream().map(ItemDto::from).toList();
        return new ParseResultDto(result.totalVolumeM3(), result.totalGrossWeightKg(),
                result.totalPalletCount(), items, result.volumeMismatchCount(), result.sheetName());
    }
}
