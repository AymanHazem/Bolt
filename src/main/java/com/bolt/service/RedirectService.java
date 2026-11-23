package com.bolt.service;

import com.bolt.config.AppConfig;
import com.bolt.dto.CachedUrlEntry;
import com.bolt.exception.AliasNotFoundException;
import com.bolt.model.UrlDocument;
import com.bolt.repository.UrlRepository;
import com.bolt.util.UrlServiceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService
{
    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;
    private final AppConfig appConfig;

    public CachedUrlEntry resolveUrl(String alias)
    {
        CachedUrlEntry cached = urlCacheService.getUrl(alias);
        if (cached != null)
        {
            log.debug("Cache hit for alias: {}", alias);
            return cached;
        }

        log.debug("Cache miss for alias: {}, querying database", alias);
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

        boolean permanent = doc.getExpiresAt() == null;
        CachedUrlEntry entry = new CachedUrlEntry(doc.getOriginalUrl(), permanent);
        Duration ttl = UrlServiceUtil.calculateTtl(doc.getExpiresAt(), appConfig.getDefaultTtlHours());
        urlCacheService.putUrl(alias, entry, ttl);
        return entry;
    }
}