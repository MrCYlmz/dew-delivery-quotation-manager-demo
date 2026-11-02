package de.dew.backend.domain;

import java.time.Instant;

public record DeliveryPdf(
        Long id,
        Long deliveryId,
        String originalFilename,
        String storedPath,
        Long sizeBytes,
        int sortOrder,
        Instant uploadedAt) {
}
