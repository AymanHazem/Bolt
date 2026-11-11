package com.bolt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfig
{
    private String baseUrl = "http://localhost:8080";
    private long defaultTtlHours = 24;
    private int aliasLength = 6;
    private int qrCodeSize = 300;
    private int qrCodeMargin = 1;
    private long qrCacheTtlDays = 7;
    private int maxAliasGenerationRetries = 10;

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

