package com.bolt.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;
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
}

