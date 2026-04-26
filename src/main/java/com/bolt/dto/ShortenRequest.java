package com.bolt.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class ShortenRequest {

    @NotNull
    @URL(message = "Must be a valid URL")
    private String originalUrl;

    @Pattern(
            regexp = "^$|^[a-zA-Z0-9_-]{3,30}$",
            message = "Alias must be 3–30 chars, letters/numbers/hyphens/underscores only"
    )
    private String customAlias;

    @Min(1)
    @Max(365)
    private Integer expiresInDays;
}
