package com.bolt.controller;

import com.bolt.dto.ShortenRequest;
import com.bolt.dto.ShortenResponse;
import com.bolt.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/urls")
@RequiredArgsConstructor
public class UrlController
{
    private final UrlService urlService;

    @PostMapping
    public ResponseEntity<ShortenResponse> createShortUrl(
        @Valid @RequestBody ShortenRequest request,
        @RequestParam(defaultValue = "false") boolean includeQr)
    {
        log.info("Creating short URL for: {}", request.getOriginalUrl().substring(0, Math.min(50, request.getOriginalUrl().length())));
        ShortenResponse response = urlService.createShortUrl(request, includeQr);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create(response.getShortUrl()))
            .body(response);
    }

    @GetMapping("/{alias:[a-zA-Z0-9_-]{3,30}}")
    public ResponseEntity<ShortenResponse> getUrlInfo(
        @PathVariable String alias,
        @RequestParam(defaultValue = "false") boolean includeQr)
    {
        ShortenResponse response = urlService.getUrlInfo(alias, includeQr);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{alias:[a-zA-Z0-9_-]{3,30}}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String alias)
    {
        urlService.deleteUrl(alias);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{alias:[a-zA-Z0-9_-]{3,30}}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(@PathVariable String alias)
    {
        byte[] qr = urlService.getQrCode(alias);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
            .body(qr);
    }
}
