package de.dew.backend.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dew.backend.domain.DeliveryPdf;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public record PdfDto(
        @JsonProperty("id") long id,
        @JsonProperty("original_filename") String originalFilename,
        @JsonProperty("size_bytes") Long sizeBytes,
        @JsonProperty("sort_order") int sortOrder,
        @JsonProperty("uploaded_at") String uploadedAt) {

    public static PdfDto from(DeliveryPdf pdf) {
        return new PdfDto(pdf.id(), pdf.originalFilename(), pdf.sizeBytes(), pdf.sortOrder(),
                format(pdf.uploadedAt()));
    }

    static String format(Instant instant) {
        return instant == null
                ? null
                : DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }
}
