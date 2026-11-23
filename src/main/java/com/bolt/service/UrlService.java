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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService
{
    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;
    private final QrService qrService;
    private final AppConfig appConfig;

    public ShortenResponse createShortUrl(ShortenRequest request)
    {
        return createShortUrl(request, false);
    }

    public ShortenResponse createShortUrl(ShortenRequest request, boolean includeQr)
    {
        validateTargetUrl(request.getOriginalUrl());

        boolean permanent = request.getExpiresInDays() == null;
        Instant now = UrlServiceUtil.now();
        Instant expiresAt = permanent ? null : now.plus(request.getExpiresInDays(), ChronoUnit.DAYS);

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
        byte[] qrBytes = includeQr ? generateAndCacheQrBytes(saved.getAlias(), saved.getExpiresAt()) : null;
        log.info("Created shortened URL: {} -> {}", saved.getAlias(), saved.getOriginalUrl());
        return mapToResponse(saved, qrBytes);
    }

    public ShortenResponse getUrlInfo(String alias)
    {
        return getUrlInfo(alias, false);
    }

    public ShortenResponse getUrlInfo(String alias, boolean includeQr)
    {
        UrlDocument doc = findActiveValidDocument(alias);
        byte[] qrBytes = includeQr ? getOrCacheQrBytes(doc.getAlias(), doc.getExpiresAt()) : null;
        return mapToResponse(doc, qrBytes);
    }

    public byte[] getQrCode(String alias)
    {
        byte[] cached = urlCacheService.getQr(alias);
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
        urlCacheService.evict(alias);
        boolean deactivated = urlRepository.deactivateActive(alias, Instant.now());
        if (!deactivated)
        {
            log.debug("Alias not found, inactive, or expired on delete: {}", alias);
            throw new AliasNotFoundException(alias);
        }
        urlCacheService.evict(alias);
        log.info("Deleted URL alias: {}", alias);
    }

    private void validateTargetUrl(String originalUrl)
    {
        if (!StringUtils.hasText(originalUrl))
        {
            throw new IllegalArgumentException("Original URL is required");
        }
        URI target = URI.create(originalUrl);
        String scheme = target.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)))
        {
            throw new IllegalArgumentException("URL scheme must be http or https");
        }
        if (!StringUtils.hasText(target.getHost()))
        {
            throw new IllegalArgumentException("URL host is required");
        }
        if (StringUtils.hasText(appConfig.getBaseUrl()))
        {
            URI base = URI.create(appConfig.getBaseUrl());
            if (base.getHost() != null
                && base.getHost().equalsIgnoreCase(target.getHost())
                && effectivePort(base) == effectivePort(target))
            {
                throw new IllegalArgumentException("Cannot shorten URLs pointing to this service");
            }
        }
    }

    private static int effectivePort(URI uri)
    {
        if (uri.getPort() != -1)
        {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private UrlDocument saveWithCustomAlias(String alias, String originalUrl,
                                            Instant now, Instant expiresAt)
    {
        String normalizedAlias = alias.toLowerCase(Locale.ROOT);
        if (CacheConstants.RESERVED_ALIASES.contains(normalizedAlias))
        {
            log.warn("Attempted to use reserved alias: {}", alias);
            throw new ReservedAliasException(alias);
        }
        UrlDocument doc = buildDocument(alias, originalUrl, true, now, expiresAt);
        try
        {
            return urlRepository.save(doc);
        }
        catch (DuplicateKeyException e)
        {
            if (urlRepository.deactivateExpired(alias, now))
            {
                try
                {
                    return urlRepository.save(doc);
                }
                catch (DuplicateKeyException retryEx)
                {
                    log.warn("Concurrent duplicate alias creation after expired cleanup for: {}", alias);
                    throw new AliasAlreadyTakenException(alias);
                }
            }
            log.warn("Alias already taken: {}", alias);
            throw new AliasAlreadyTakenException(alias);
        }
    }

    private UrlDocument saveWithGeneratedAlias(String originalUrl, Instant now, Instant expiresAt)
    {
        for (int attempt = 0; attempt < appConfig.getMaxAliasGenerationRetries(); attempt++)
        {
            String candidate = UrlServiceUtil.generateRandomAlias(appConfig.getAliasLength());
            if (CacheConstants.RESERVED_ALIASES.contains(candidate.toLowerCase(Locale.ROOT)))
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
                log.debug("Random alias collision for candidate: {}, retrying", candidate);
            }
        }
        throw new IllegalStateException("Failed to generate unique alias after " +
            appConfig.getMaxAliasGenerationRetries() + " attempts");
    }

    private UrlDocument buildDocument(String alias, String originalUrl, boolean customAlias,
                                      Instant now, Instant expiresAt)
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
                log.debug("Alias not found or inactive: {}", alias);
                return new AliasNotFoundException(alias);
            });
        if (UrlServiceUtil.isExpired(doc.getExpiresAt()))
        {
            log.debug("URL expired: {}", alias);
            throw new AliasNotFoundException(alias);
        }
        return doc;
    }

    /**
     * Cache a URL entry in Redis with appropriate TTL.
     */
    private void cacheUrlEntry(String alias, String originalUrl, boolean permanent, Instant expiresAt)
    {
        CachedUrlEntry entry = new CachedUrlEntry(originalUrl, permanent);
        Duration ttl = UrlServiceUtil.calculateTtl(expiresAt, appConfig.getDefaultTtlHours());
        urlCacheService.putUrl(alias, entry, ttl);
    }

    private byte[] getOrCacheQrBytes(String alias, Instant expiresAt)
    {
        byte[] cached = urlCacheService.getQr(alias);
        if (cached != null)
        {
            return cached;
        }
        return generateAndCacheQrBytes(alias, expiresAt);
    }

    private byte[] generateAndCacheQrBytes(String alias, Instant expiresAt)
    {
        String shortUrl = buildShortUrl(alias);
        byte[] qrBytes = qrService.generateQr(shortUrl);
        Duration qrTtl = calculateQrCacheTtl(expiresAt);
        urlCacheService.putQr(alias, qrBytes, qrTtl);
        return qrBytes;
    }

    private Duration calculateQrCacheTtl(Instant expiresAt)
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
        String qrCode = qrBytes != null
            ? "data:image/png;base64," + Base64.getEncoder().encodeToString(qrBytes)
            : null;
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
