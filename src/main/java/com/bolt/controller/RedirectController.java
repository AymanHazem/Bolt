package com.bolt.controller;

import com.bolt.config.AppConfig;
import com.bolt.dto.CachedUrlEntry;
import com.bolt.service.RedirectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RedirectController
{
    private final RedirectService redirectService;
    private final AppConfig appConfig;

    @GetMapping("/{alias:[a-zA-Z0-9_-]{3,30}}")
    public ResponseEntity<Void> redirect(@PathVariable String alias)
    {
        CachedUrlEntry entry = redirectService.resolveUrl(alias);
        HttpStatus status = entry.permanent() ? HttpStatus.MOVED_PERMANENTLY : HttpStatus.FOUND;
        log.debug("Redirecting alias {} with status {}", alias, status);
        ResponseEntity.BodyBuilder builder = ResponseEntity
            .status(status)
            .header(HttpHeaders.LOCATION, entry.originalUrl());
        if (entry.permanent())
        {
            builder.cacheControl(
                CacheControl.maxAge(Duration.ofSeconds(appConfig.getRedirectCacheMaxAgeSeconds()))
                    .cachePrivate()
            );
        }
        return builder.build();
    }
}
