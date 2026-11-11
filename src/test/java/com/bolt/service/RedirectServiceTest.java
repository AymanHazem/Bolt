package com.bolt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bolt.config.AppConfig;
import com.bolt.dto.CachedUrlEntry;
import com.bolt.exception.AliasNotFoundException;
import com.bolt.model.UrlDocument;
import com.bolt.repository.UrlRepository;
import com.bolt.util.CacheConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @Mock
    private UrlRepository urlRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> stringValueOps;

    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();
        appConfig.setDefaultTtlHours(24);
        lenient().when(redisTemplate.opsForValue()).thenReturn(stringValueOps);
        redirectService = new RedirectService(urlRepository, redisTemplate, new ObjectMapper(), appConfig);
    }

    @Test
    void resolveUrlReturnsCachedEntryWithoutHittingMongo() {
        when(stringValueOps.get(CacheConstants.URL_CACHE_PREFIX + "fast"))
                .thenReturn("{\"originalUrl\":\"https://example.com\",\"permanent\":true}");

        CachedUrlEntry entry = redirectService.resolveUrl("fast");

        assertThat(entry.originalUrl()).isEqualTo("https://example.com");
        assertThat(entry.permanent()).isTrue();
        verify(urlRepository, never()).findByAliasAndActiveTrue(anyString());
    }

    @Test
    void resolveUrlDeletesCorruptedCacheAndFallsBackToMongo() {
        UrlDocument doc = UrlDocument.builder()
                .alias("heal")
                .originalUrl("https://example.com/recovered")
                .active(true)
                .build();
        when(stringValueOps.get(CacheConstants.URL_CACHE_PREFIX + "heal")).thenReturn("{bad-json");
        when(urlRepository.findByAliasAndActiveTrue("heal")).thenReturn(Optional.of(doc));

        CachedUrlEntry entry = redirectService.resolveUrl("heal");

        assertThat(entry.originalUrl()).isEqualTo("https://example.com/recovered");
        assertThat(entry.permanent()).isTrue();
        verify(redisTemplate).delete(CacheConstants.URL_CACHE_PREFIX + "heal");
        verify(stringValueOps).set(eq(CacheConstants.URL_CACHE_PREFIX + "heal"), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void resolveUrlRejectsExpiredMongoDocument() {
        UrlDocument expired = UrlDocument.builder()
                .alias("exp")
                .originalUrl("https://example.com")
                .expiresAt(LocalDateTime.now().minusSeconds(5))
                .active(true)
                .build();
        when(stringValueOps.get(CacheConstants.URL_CACHE_PREFIX + "exp")).thenReturn(null);
        when(urlRepository.findByAliasAndActiveTrue("exp")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> redirectService.resolveUrl("exp"))
                .isInstanceOf(AliasNotFoundException.class);
        verify(stringValueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
