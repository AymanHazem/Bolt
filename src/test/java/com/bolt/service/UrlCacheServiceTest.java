package com.bolt.service;

import com.bolt.dto.CachedUrlEntry;
import com.bolt.util.CacheConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private RedisTemplate<String, byte[]> redisByteTemplate;
    @Mock
    private ValueOperations<String, String> stringValueOps;
    @Mock
    private ValueOperations<String, byte[]> byteValueOps;

    private UrlCacheService urlCacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(stringValueOps);
        lenient().when(redisByteTemplate.opsForValue()).thenReturn(byteValueOps);
        urlCacheService = new UrlCacheService(redisTemplate, redisByteTemplate);
    }

    @Test
    void failsOpenWhenRedisThrowsConnectionFailure() {
        when(stringValueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down"))
                .when(stringValueOps).set(anyString(), anyString(), any(Duration.class));
        when(byteValueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down"))
                .when(byteValueOps).set(anyString(), any(byte[].class), any(Duration.class));
        when(redisTemplate.delete(anyCollection())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(urlCacheService.getUrl("abc")).isNull();
        assertThat(urlCacheService.getQr("abc")).isNull();
        assertThatCode(() -> urlCacheService.putUrl("abc", new CachedUrlEntry("https://example.com", true), Duration.ofMinutes(5)))
                .doesNotThrowAnyException();
        assertThatCode(() -> urlCacheService.putQr("abc", new byte[] {1, 2}, Duration.ofMinutes(5)))
                .doesNotThrowAnyException();
        assertThatCode(() -> urlCacheService.evict("abc"))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsWriteWhenTtlIsZeroOrNegativeAndBatchesEvict() {
        urlCacheService.putUrl("abc", new CachedUrlEntry("https://example.com", false), Duration.ZERO);
        urlCacheService.putUrl("abc", new CachedUrlEntry("https://example.com", false), Duration.ofSeconds(-1));
        urlCacheService.putQr("abc", new byte[] {1}, Duration.ZERO);

        verify(stringValueOps, never()).set(anyString(), anyString(), any(Duration.class));
        verify(byteValueOps, never()).set(anyString(), any(byte[].class), any(Duration.class));

        urlCacheService.evict("abc");
        verify(redisTemplate).delete(List.of(
                CacheConstants.URL_CACHE_PREFIX + "abc",
                CacheConstants.QR_CACHE_PREFIX + "abc"
        ));
    }
}
