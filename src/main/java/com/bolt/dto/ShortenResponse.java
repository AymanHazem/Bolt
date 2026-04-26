package com.bolt.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

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
