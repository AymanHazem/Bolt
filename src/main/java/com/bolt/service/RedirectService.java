package com.bolt.service;
import com.bolt.dto.CachedUrlEntry;
import com.bolt.exception.AliasNotFoundException;
import com.bolt.model.UrlDocument;
import com.bolt.repository.UrlRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class RedirectService
{
    private static final String URL_CACHE_PREFIX = "url:";
    private final UrlRepository urlRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    public CachedUrlEntry resolveUrl(String alias)
    {
        String json = redisTemplate.opsForValue().get(URL_CACHE_PREFIX + alias);
        if (json != null)
        {
            try
            {
                return objectMapper.readValue(json, CachedUrlEntry.class);
            }
            catch (JsonProcessingException e)
            {
                redisTemplate.delete(URL_CACHE_PREFIX + alias);
            }
        }

        UrlDocument doc = urlRepository.findByAliasAndActiveTrue(alias).orElseThrow(() -> new AliasNotFoundException(alias));

        Duration ttl;
        boolean permanent;
        if (doc.getExpiresAt() != null)
        {
            ttl = Duration.between(LocalDateTime.now(), doc.getExpiresAt());
            if (ttl.isNegative() || ttl.isZero())
                throw new AliasNotFoundException(alias);

            permanent = false;
        } else
        {
            ttl = Duration.ofHours(24);
            permanent = true;
        }

        CachedUrlEntry entry = new CachedUrlEntry(doc.getOriginalUrl(), permanent);
        try {
            redisTemplate.opsForValue().set(
                    URL_CACHE_PREFIX + alias,
                    objectMapper.writeValueAsString(entry),
                    ttl
            );
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalStateException("Failed to serialize URL cache entry", e);
        }
        return entry;
    }
}
