package com.bolt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ShortenResponse {

    private String alias;
    private String shortUrl;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String qrCode;
    private String originalUrl;
    private Instant expiresAt;
    private Instant createdAt;
    private boolean permanent;
}
