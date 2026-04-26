package com.bolt.controller;
import com.bolt.dto.ShortenRequest;
import com.bolt.dto.ShortenResponse;
import com.bolt.service.QrService;
import com.bolt.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
@RestController
@RequestMapping("/urls")
@RequiredArgsConstructor
public class UrlController
{
    private static final String QR_CACHE_PREFIX = "qr:";
    private final UrlService urlService;
    private final QrService qrService;
    private final RedisTemplate<String, byte[]> redisByteTemplate;
    @Value("${app.base-url}")
    private String baseUrl;
    @PostMapping
    public ResponseEntity<ShortenResponse> createShortUrl(@Valid @RequestBody ShortenRequest request)
    {
        ShortenResponse response = urlService.createShortUrl(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create(response.getShortUrl()))
                .body(response);
    }

    @GetMapping("/{alias}")
    public ResponseEntity<ShortenResponse> getUrlInfo(@PathVariable String alias)
    {
        ShortenResponse response = urlService.getUrlInfo(alias);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{alias}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String alias)
    {
        urlService.deleteUrl(alias);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{alias}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(@PathVariable String alias)
    {
        // Check QR cache first
        byte[] cached = redisByteTemplate.opsForValue().get(QR_CACHE_PREFIX + alias);
        if (cached != null)
        {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(cached);
        }

        // Verify alias exists (throws 404 if not found)
        urlService.getUrlInfo(alias);
        // Generate QR code
        byte[] qr = qrService.generateQr(baseUrl + "/" + alias);
        // Cache for 7 days
        redisByteTemplate.opsForValue().set(QR_CACHE_PREFIX + alias, qr, Duration.ofDays(7));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qr);
    }
}
