package com.bolt.service;

import com.bolt.config.AppConfig;
import com.bolt.dto.CachedUrlEntry;
import com.bolt.exception.AliasNotFoundException;
import com.bolt.model.UrlDocument;
import com.bolt.repository.UrlRepository;
import com.bolt.util.CacheConstants;
import com.bolt.util.UrlServiceUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService
{
    private final UrlRepository urlRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;
    public CachedUrlEntry resolveUrl(String alias)
    {
        CachedUrlEntry cached = getCachedUrlEntry(alias);
        if (cached != null)
        {
            log.debug("Cache hit for alias: {}", alias);
            return cached;
        }

        // Fall back to database
        log.debug("Cache miss for alias: {}, querying database", alias);
        UrlDocument doc = urlRepository.findByAliasAndActiveTrue(alias)
            .orElseThrow(() ->
            {
                log.warn("Alias not found or inactive: {}", alias);
                return new AliasNotFoundException(alias);
            });

        // Check if expired
        if (UrlServiceUtil.isExpired(doc.getExpiresAt()))
        {
            log.warn("URL expired: {}", alias);
            throw new AliasNotFoundException(alias);
        }
        // Re-cache for future requests
        boolean permanent = doc.getExpiresAt() == null;
        cacheUrlEntry(alias, doc.getOriginalUrl(), permanent, doc.getExpiresAt());
        return new CachedUrlEntry(doc.getOriginalUrl(), permanent);
    }

    private CachedUrlEntry getCachedUrlEntry(String alias)
    {
        String json = redisTemplate.opsForValue().get(CacheConstants.URL_CACHE_PREFIX + alias);
        if (json != null)
        {
            try
            {
                return objectMapper.readValue(json, CachedUrlEntry.class);
            }
            catch (JsonProcessingException e)
            {
                log.warn("Failed to deserialize cached URL entry for alias: {}, clearing cache", alias, e);
                redisTemplate.delete(CacheConstants.URL_CACHE_PREFIX + alias);
            }
        }
        return null;
    }

    private void cacheUrlEntry(String alias, String originalUrl, boolean permanent, 
                               java.time.LocalDateTime expiresAt)
    {
        CachedUrlEntry entry = new CachedUrlEntry(originalUrl, permanent);
        try
        {
            String json = objectMapper.writeValueAsString(entry);
            Duration ttl = UrlServiceUtil.calculateTtl(expiresAt, appConfig.getDefaultTtlHours());
            redisTemplate.opsForValue().set(CacheConstants.URL_CACHE_PREFIX + alias, json, ttl);
            log.debug("Cached URL entry for alias: {} with TTL: {}", alias, ttl);
        }
        catch (JsonProcessingException e)
        {
            log.error("Failed to cache URL entry for alias: {}", alias, e);
        }
    }
}