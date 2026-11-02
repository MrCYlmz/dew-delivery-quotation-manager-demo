package de.dew.backend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("dew")
public record DewProperties(
        @NotBlank(message = "dew.storage-dir (DEW_STORAGE_DIR) must name the upload directory")
        String storageDir,
        boolean openBrowser) {
}
