package com.bolt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "urls")
public class UrlDocument
{

    @Id
    private String id;

    @Indexed(unique = true, partialFilter = "{ 'active': true }")
    private String alias;

    private String originalUrl;

    private boolean customAlias;

    private LocalDateTime createdAt;

    @Indexed(expireAfter = "PT0S")
    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean active = true;
}
