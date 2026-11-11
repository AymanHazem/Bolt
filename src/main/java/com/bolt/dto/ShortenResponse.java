package com.bolt.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ShortenResponse {

    private String alias;
    private String shortUrl;
    private String qrCode;
    private String originalUrl;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private boolean permanent;
}
