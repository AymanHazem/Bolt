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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ObjectMapper objectMapper;
    private final QrService qrService;
    private final AppConfig appConfig;
    public ShortenResponse createShortUrl(ShortenRequest request)
    {
        String alias;
        String requestedCustomAlias = request.getCustomAlias();
        
        if (StringUtils.hasText(requestedCustomAlias))
        {
            alias = requestedCustomAlias.trim();
            String normalizedAlias = alias.toLowerCase(Locale.ROOT);
            
            if (CacheConstants.RESERVED_ALIASES.contains(normalizedAlias))
            {
                log.warn("Attempted to use reserved alias: {}", alias);
                throw new ReservedAliasException(alias);
            }
            if (urlRepository.existsByAlias(alias))
            {
                log.warn("Alias already taken: {}", alias);
                throw new AliasAlreadyTakenException(alias);
            }
        }
        else
            // Generate random alias with retry logic to prevent infinite loops
            alias = generateAliasWithRetry(appConfig.getAliasLength());

        boolean permanent = request.getExpiresInDays() == null;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = permanent ? null : now.plusDays(request.getExpiresInDays());

        UrlDocument saved = urlRepository.save(
            UrlDocument.builder()
                .alias(alias)
                .originalUrl(request.getOriginalUrl())
                .customAlias(StringUtils.hasText(requestedCustomAlias))
                .createdAt(now)
                .expiresAt(expiresAt)
                .active(true)
                .build()
        );
        // Cache the URL entry in Redis
        cacheUrlEntry(alias, saved.getOriginalUrl(), permanent);
        log.info("Created shortened URL: {} -> {}", alias, saved.getOriginalUrl());
        return mapToResponse(saved);
    }

    public ShortenResponse getUrlInfo(String alias)
    {
        UrlDocument doc = urlRepository.findByAliasAndActiveTrue(alias)
            .orElseThrow(() ->
            {
                log.warn("Alias not found or inactive: {}", alias);
                return new AliasNotFoundException(alias);
            });
        return mapToResponse(doc);
    }

    public void deleteUrl(String alias)
    {
        UrlDocument doc = urlRepository.findByAliasAndActiveTrue(alias)
            .orElseThrow(() ->
            {
                log.warn("Failed to delete - alias not found: {}", alias);
                return new AliasNotFoundException(alias);
            });
        doc.setActive(false);
        urlRepository.save(doc);
        redisTemplate.delete(CacheConstants.URL_CACHE_PREFIX + alias);
        log.info("Deleted URL alias: {}", alias);
    }

    /**
     * Generate random alias with retry logic to avoid infinite loops
     */
    private String generateAliasWithRetry(int length)
    {
        for (int attempt = 0; attempt < appConfig.getMaxAliasGenerationRetries(); attempt++)
        {
            String candidate = UrlServiceUtil.generateRandomAlias(length);
            if (!urlRepository.existsByAlias(candidate))
                return candidate;

        }
        throw new IllegalStateException("Failed to generate unique alias after " + 
            appConfig.getMaxAliasGenerationRetries() + " attempts");
    }

    /**
     * Cache a URL entry in Redis with appropriate TTL
     */
    private void cacheUrlEntry(String alias, String originalUrl, boolean permanent)
    {
        CachedUrlEntry entry = new CachedUrlEntry(originalUrl, permanent);
        try
        {
            String json = objectMapper.writeValueAsString(entry);
            Duration ttl = UrlServiceUtil.calculateTtl(null, appConfig.getDefaultTtlHours());
            redisTemplate.opsForValue().set(CacheConstants.URL_CACHE_PREFIX + alias, json, ttl);
        }
        catch (JsonProcessingException e)
        {
            log.error("Failed to cache URL entry for alias: {}", alias, e);
            throw new IllegalStateException("Failed to serialize URL cache entry", e);
        }
    }
    private ShortenResponse mapToResponse(UrlDocument doc)
    {
        boolean permanent = doc.getExpiresAt() == null;
        String shortUrl = appConfig.getBaseUrl() + "/" + doc.getAlias();
        String qrCode = "data:image/png;base64," + Base64.getEncoder()
            .encodeToString(qrService.generateQr(shortUrl));
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
