package com.bolt.service;

import com.bolt.config.AppConfig;
import com.bolt.dto.CachedUrlEntry;
import com.bolt.dto.ShortenRequest;
import com.bolt.dto.ShortenResponse;
import com.bolt.exception.AliasAlreadyTakenException;
import com.bolt.exception.AliasNotFoundException;
import com.bolt.exception.ReservedAliasException;
import com.bolt.model.UrlDocument;
import com.bolt.repository.UrlRepository;
import com.bolt.util.CacheConstants;
import com.bolt.util.UrlServiceUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService
{
    private final UrlRepository urlRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, byte[]> redisByteTemplate;
    private final ObjectMapper objectMapper;
    private final QrService qrService;
    private final AppConfig appConfig;

    public ShortenResponse createShortUrl(ShortenRequest request)
    {
        URI.create(request.getOriginalUrl());

        boolean permanent = request.getExpiresInDays() == null;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = permanent ? null : now.plusDays(request.getExpiresInDays());

        String requestedCustomAlias = request.getCustomAlias();
        UrlDocument saved;
        if (StringUtils.hasText(requestedCustomAlias))
        {
            saved = saveWithCustomAlias(requestedCustomAlias.trim(), request.getOriginalUrl(), now, expiresAt);
        }
        else
        {
            saved = saveWithGeneratedAlias(request.getOriginalUrl(), now, expiresAt);
        }

        cacheUrlEntry(saved.getAlias(), saved.getOriginalUrl(), permanent, saved.getExpiresAt());
        byte[] qrBytes = generateAndCacheQrBytes(saved.getAlias(), saved.getExpiresAt());
        log.info("Created shortened URL: {} -> {}", saved.getAlias(), saved.getOriginalUrl());
        return mapToResponse(saved, qrBytes);
    }

    public ShortenResponse getUrlInfo(String alias)
    {
        UrlDocument doc = findActiveValidDocument(alias);
        byte[] qrBytes = getOrCacheQrBytes(doc.getAlias(), doc.getExpiresAt());
        return mapToResponse(doc, qrBytes);
    }

    public byte[] getQrCode(String alias)
    {
        byte[] cached = redisByteTemplate.opsForValue().get(CacheConstants.QR_CACHE_PREFIX + alias);
        if (cached != null)
        {
            log.debug("Cache hit for QR code: {}", alias);
            return cached;
        }
        log.debug("Cache miss for QR code: {}, generating", alias);
        UrlDocument doc = findActiveValidDocument(alias);
        return generateAndCacheQrBytes(doc.getAlias(), doc.getExpiresAt());
    }

    public void deleteUrl(String alias)
    {
        UrlDocument doc = findActiveValidDocument(alias);
        doc.setActive(false);
        urlRepository.save(doc);
        redisTemplate.delete(CacheConstants.URL_CACHE_PREFIX + alias);
        redisByteTemplate.delete(CacheConstants.QR_CACHE_PREFIX + alias);
        log.info("Deleted URL alias: {}", alias);
    }

    private UrlDocument saveWithCustomAlias(String alias, String originalUrl,
                                            LocalDateTime now, LocalDateTime expiresAt)
    {
        String normalizedAlias = alias.toLowerCase(Locale.ROOT);
        if (CacheConstants.RESERVED_ALIASES.contains(normalizedAlias))
        {
            log.warn("Attempted to use reserved alias: {}", alias);
            throw new ReservedAliasException(alias);
        }
        if (urlRepository.existsByAliasAndActiveTrue(alias))
        {
            log.warn("Alias already taken: {}", alias);
            throw new AliasAlreadyTakenException(alias);
        }
        UrlDocument doc = buildDocument(alias, originalUrl, true, now, expiresAt);
        try
        {
            return urlRepository.save(doc);
        }
        catch (DuplicateKeyException e)
        {
            log.warn("Concurrent duplicate alias creation for: {}", alias);
            throw new AliasAlreadyTakenException(alias);
        }
    }

    private UrlDocument saveWithGeneratedAlias(String originalUrl, LocalDateTime now, LocalDateTime expiresAt)
    {
        for (int attempt = 0; attempt < appConfig.getMaxAliasGenerationRetries(); attempt++)
        {
            String candidate = UrlServiceUtil.generateRandomAlias(appConfig.getAliasLength());
            if (CacheConstants.RESERVED_ALIASES.contains(candidate.toLowerCase(Locale.ROOT))
                || urlRepository.existsByAliasAndActiveTrue(candidate))
            {
                continue;
            }
            UrlDocument doc = buildDocument(candidate, originalUrl, false, now, expiresAt);
            try
            {
                return urlRepository.save(doc);
            }
            catch (DuplicateKeyException e)
            {
                log.debug("Concurrent random alias collision for candidate: {}, retrying", candidate);
            }
        }
        throw new IllegalStateException("Failed to generate unique alias after " +
            appConfig.getMaxAliasGenerationRetries() + " attempts");
    }

    private UrlDocument buildDocument(String alias, String originalUrl, boolean customAlias,
                                      LocalDateTime now, LocalDateTime expiresAt)
    {
        return UrlDocument.builder()
            .alias(alias)
            .originalUrl(originalUrl)
            .customAlias(customAlias)
            .createdAt(now)
            .expiresAt(expiresAt)
            .active(true)
            .build();
    }

    private UrlDocument findActiveValidDocument(String alias)
    {
        UrlDocument doc = urlRepository.findByAliasAndActiveTrue(alias)
            .orElseThrow(() ->
            {
                log.warn("Alias not found or inactive: {}", alias);
                return new AliasNotFoundException(alias);
            });
        if (UrlServiceUtil.isExpired(doc.getExpiresAt()))
        {
            log.warn("URL expired: {}", alias);
            throw new AliasNotFoundException(alias);
        }
        return doc;
    }

    /**
     * Cache a URL entry in Redis with appropriate TTL.
     */
    private void cacheUrlEntry(String alias, String originalUrl, boolean permanent, LocalDateTime expiresAt)
    {
        CachedUrlEntry entry = new CachedUrlEntry(originalUrl, permanent);
        try
        {
            String json = objectMapper.writeValueAsString(entry);
            Duration ttl = UrlServiceUtil.calculateTtl(expiresAt, appConfig.getDefaultTtlHours());
            redisTemplate.opsForValue().set(CacheConstants.URL_CACHE_PREFIX + alias, json, ttl);
        }
        catch (JsonProcessingException e)
        {
            log.error("Failed to cache URL entry for alias: {}", alias, e);
            throw new IllegalStateException("Failed to serialize URL cache entry", e);
        }
    }

    private byte[] getOrCacheQrBytes(String alias, LocalDateTime expiresAt)
    {
        byte[] cached = redisByteTemplate.opsForValue().get(CacheConstants.QR_CACHE_PREFIX + alias);
        if (cached != null)
        {
            return cached;
        }
        return generateAndCacheQrBytes(alias, expiresAt);
    }

    private byte[] generateAndCacheQrBytes(String alias, LocalDateTime expiresAt)
    {
        String shortUrl = buildShortUrl(alias);
        byte[] qrBytes = qrService.generateQr(shortUrl);
        Duration qrTtl = calculateQrCacheTtl(expiresAt);
        redisByteTemplate.opsForValue().set(CacheConstants.QR_CACHE_PREFIX + alias, qrBytes, qrTtl);
        return qrBytes;
    }

    private Duration calculateQrCacheTtl(LocalDateTime expiresAt)
    {
        Duration maxQrTtl = Duration.ofDays(appConfig.getQrCacheTtlDays());
        if (expiresAt == null)
        {
            return maxQrTtl;
        }
        Duration remaining = UrlServiceUtil.calculateTtl(expiresAt, appConfig.getDefaultTtlHours());
        return remaining.compareTo(maxQrTtl) < 0 ? remaining : maxQrTtl;
    }

    private String buildShortUrl(String alias)
    {
        return appConfig.getBaseUrl() + "/" + alias;
    }

    private ShortenResponse mapToResponse(UrlDocument doc, byte[] qrBytes)
    {
        boolean permanent = doc.getExpiresAt() == null;
        String shortUrl = buildShortUrl(doc.getAlias());
        String qrCode = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrBytes);
        return ShortenResponse.builder()
            .alias(doc.getAlias())
            .shortUrl(shortUrl)
            .qrCode(qrCode)
            .originalUrl(doc.getOriginalUrl())
            .expiresAt(doc.getExpiresAt())
            .createdAt(doc.getCreatedAt())
            .permanent(permanent)
            .build();
    }
}
