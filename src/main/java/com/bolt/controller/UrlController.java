package com.bolt.controller;
import com.bolt.config.AppConfig;
import com.bolt.dto.ShortenRequest;
import com.bolt.dto.ShortenResponse;
import com.bolt.service.QrService;
import com.bolt.service.UrlService;
import com.bolt.util.CacheConstants;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Slf4j
@RestController
@RequestMapping("/urls")
@RequiredArgsConstructor
public class UrlController
{
    private final UrlService urlService;
    private final QrService qrService;
    private final RedisTemplate<String, byte[]> redisByteTemplate;
    private final AppConfig appConfig;
    @PostMapping
    public ResponseEntity<ShortenResponse> createShortUrl(@Valid @RequestBody ShortenRequest request)
    {
        log.info("Creating short URL for: {}", request.getOriginalUrl().substring(0, Math.min(50, request.getOriginalUrl().length())));
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
        // Check QR cache first (7-day cache)
        byte[] cached = redisByteTemplate.opsForValue().get(CacheConstants.QR_CACHE_PREFIX + alias);
        if (cached != null)
        {
            log.debug("Cache hit for QR code: {}", alias);
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(cached);
        }
        log.debug("Cache miss for QR code: {}, generating", alias);
        // Verify alias exists (throws 404 if not found)
        urlService.getUrlInfo(alias);
        // Generate QR code
        byte[] qr = qrService.generateQr(appConfig.getBaseUrl() + "/" + alias);
        // Cache for configured duration (default 7 days)
        Duration cacheTtl = Duration.ofDays(appConfig.getQrCacheTtlDays());
        redisByteTemplate.opsForValue().set(CacheConstants.QR_CACHE_PREFIX + alias, qr, cacheTtl);
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(qr);
    }
}
