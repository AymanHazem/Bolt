package com.bolt.service;
import com.bolt.dto.CachedUrlEntry;
import com.bolt.dto.ShortenRequest;
import com.bolt.dto.ShortenResponse;
import com.bolt.exception.AliasAlreadyTakenException;
import com.bolt.exception.AliasNotFoundException;
import com.bolt.exception.ReservedAliasException;
import com.bolt.model.UrlDocument;
import com.bolt.repository.UrlRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
@Service
@RequiredArgsConstructor
public class UrlService
{
    private static final String URL_CACHE_PREFIX = "url:";
    private static final String CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Set<String> RESERVED = Set.of(
            "api", "admin", "health", "login", "register",
            "docs", "static", "assets", "favicon", "qr", "info"
    );
    private final UrlRepository urlRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final QrService qrService;
    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.default-ttl-hours:24}")
    private long defaultTtlHours;

    public ShortenResponse createShortUrl(ShortenRequest request)
    {
        String alias;
        String requestedCustomAlias = request.getCustomAlias();
        if (StringUtils.hasText(requestedCustomAlias))
        {
            alias = requestedCustomAlias.trim();
            String normalizedAlias = alias.toLowerCase(Locale.ROOT);
            if (RESERVED.contains(normalizedAlias))
                throw new ReservedAliasException(alias);

            if (urlRepository.existsByAlias(alias))
                throw new AliasAlreadyTakenException(alias);

        }
        else
        {
            do
            {
                alias = ThreadLocalRandom.current()
                        .ints(6, 0, CHARS.length())
                        .mapToObj(CHARS::charAt)
                        .map(String::valueOf)
                        .collect(Collectors.joining());
            }
            while (urlRepository.existsByAlias(alias));
        }

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

        CachedUrlEntry entry = new CachedUrlEntry(saved.getOriginalUrl(), permanent);
        String json;
        try
        {
            json = objectMapper.writeValueAsString(entry);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalStateException("Failed to serialize URL cache entry", e);
        }

        Duration ttl;
        if (expiresAt != null)
        {
            ttl = Duration.between(LocalDateTime.now(), expiresAt);
            if (ttl.isNegative() || ttl.isZero())
                ttl = Duration.ofSeconds(1);

        }
        else
            ttl = Duration.ofHours(defaultTtlHours);

        redisTemplate.opsForValue().set(URL_CACHE_PREFIX + alias, json, ttl);

        return mapToResponse(saved);
    }

    public ShortenResponse getUrlInfo(String alias)
    {
        UrlDocument doc = urlRepository.findByAliasAndActiveTrue(alias)
                .orElseThrow(() -> new AliasNotFoundException(alias));
        return mapToResponse(doc);
    }

    public void deleteUrl(String alias)
    {
        UrlDocument doc = urlRepository.findByAliasAndActiveTrue(alias)
                .orElseThrow(() -> new AliasNotFoundException(alias));
        doc.setActive(false);
        urlRepository.save(doc);
        redisTemplate.delete(URL_CACHE_PREFIX + alias);
    }

    private ShortenResponse mapToResponse(UrlDocument doc)
    {
        boolean permanent = doc.getExpiresAt() == null;
        String shortUrl = baseUrl + "/" + doc.getAlias();
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
