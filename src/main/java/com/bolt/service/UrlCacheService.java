package com.bolt.service;

import com.bolt.dto.CachedUrlEntry;
import com.bolt.util.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCacheService
{
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, byte[]> redisByteTemplate;

    public CachedUrlEntry getUrl(String alias)
    {
        String key = CacheConstants.URL_CACHE_PREFIX + alias;
        try
        {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null)
            {
                return null;
            }
            CachedUrlEntry decoded = CachedUrlEntry.decode(raw);
            if (decoded == null)
            {
                log.warn("Unreadable or legacy cached URL entry for alias: {}, evicting", alias);
                redisTemplate.delete(key);
            }
            return decoded;
        }
        catch (DataAccessException e)
        {
            log.warn("Redis read failed for URL alias: {}, falling back to MongoDB", alias, e);
            return null;
        }
    }

    public void putUrl(String alias, CachedUrlEntry entry, Duration ttl)
    {
        if (ttl == null || ttl.isNegative() || ttl.isZero())
        {
            return;
        }
        try
        {
            redisTemplate.opsForValue().set(CacheConstants.URL_CACHE_PREFIX + alias, entry.encode(), ttl);
        }
        catch (DataAccessException e)
        {
            log.warn("Redis write failed for URL alias: {}", alias, e);
        }
    }

    public byte[] getQr(String alias)
    {
        try
        {
            return redisByteTemplate.opsForValue().get(CacheConstants.QR_CACHE_PREFIX + alias);
        }
        catch (DataAccessException e)
        {
            log.warn("Redis read failed for QR alias: {}", alias, e);
            return null;
        }
    }

    public void putQr(String alias, byte[] pngBytes, Duration ttl)
    {
        if (ttl == null || ttl.isNegative() || ttl.isZero())
        {
            return;
        }
        try
        {
            redisByteTemplate.opsForValue().set(CacheConstants.QR_CACHE_PREFIX + alias, pngBytes, ttl);
        }
        catch (DataAccessException e)
        {
            log.warn("Redis write failed for QR alias: {}", alias, e);
        }
    }

    public void evict(String alias)
    {
        try
        {
            redisTemplate.delete(List.of(
                CacheConstants.URL_CACHE_PREFIX + alias,
                CacheConstants.QR_CACHE_PREFIX + alias
            ));
        }
        catch (DataAccessException e)
        {
            log.warn("Redis eviction failed for alias: {}", alias, e);
        }
    }
}
