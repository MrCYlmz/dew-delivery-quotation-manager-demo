package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dew.backend.domain.DeliveryItem;
import de.dew.parser.PackingListItem;
import java.math.BigDecimal;

public record ItemDto(
        @JsonProperty("source_row") int sourceRow,
        @JsonProperty("description") String description,
        @JsonProperty("article_no") String articleNo,
        @JsonProperty("length_mm") BigDecimal lengthMm,
        @JsonProperty("width_mm") BigDecimal widthMm,
        @JsonProperty("height_mm") BigDecimal heightMm,
        @JsonProperty("pallet_count") BigDecimal palletCount,
        @JsonProperty("volume_m3") BigDecimal volumeM3,
        @JsonProperty("gross_weight_kg") BigDecimal grossWeightKg,
        @JsonProperty("net_weight_kg") BigDecimal netWeightKg,
        @JsonProperty("volume_mismatch") boolean volumeMismatch) {

    public static ItemDto from(DeliveryItem item) {
        return new ItemDto(item.sourceRow(), item.description(), item.articleNo(), item.lengthMm(),
                item.widthMm(), item.heightMm(), item.palletCount(), item.volumeM3(),
                item.grossWeightKg(), item.netWeightKg(), item.volumeMismatch());
    }

    public static ItemDto from(PackingListItem item) {
        return new ItemDto(item.sourceRow(), item.description(), item.articleNo(), item.lengthMm(),
                item.widthMm(), item.heightMm(), item.palletCount(), item.volumeM3(),
                item.grossWeightKg(), item.netWeightKg(), item.volumeMismatch());
    }
}
