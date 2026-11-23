package com.bolt.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig
{
    @NotBlank
    @URL(regexp = "^(?i)https?://.+", message = "Base URL must be a valid http or https URL")
    private String baseUrl = "http://localhost:8080";

    @Min(1)
    private long defaultTtlHours = 24;

    @Min(4)
    @Max(30)
    private int aliasLength = 6;

    @Min(21)
    @Max(2000)
    private int qrCodeSize = 300;

    @Min(0)
    @Max(20)
    private int qrCodeMargin = 1;

    @Min(1)
    private long qrCacheTtlDays = 7;

    @Min(1)
    private int maxAliasGenerationRetries = 10;

    @Min(0)
    private long redirectCacheMaxAgeSeconds = 3600;

    /**
     * Normalize configured baseUrl by trimming whitespace and stripping trailing slashes.
     */
    public void setBaseUrl(String baseUrl)
    {
        if (baseUrl == null)
        {
            this.baseUrl = null;
            return;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUrl = normalized;
    }
}

