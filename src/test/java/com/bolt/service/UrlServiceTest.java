package com.bolt.service;

import com.bolt.config.AppConfig;
import com.bolt.dto.ShortenRequest;
import com.bolt.dto.ShortenResponse;
import com.bolt.exception.AliasAlreadyTakenException;
import com.bolt.exception.AliasNotFoundException;
import com.bolt.exception.ReservedAliasException;
import com.bolt.model.UrlDocument;
import com.bolt.repository.UrlRepository;
import com.bolt.util.CacheConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private RedisTemplate<String, byte[]> redisByteTemplate;
    @Mock
    private ValueOperations<String, String> stringValueOps;
    @Mock
    private ValueOperations<String, byte[]> byteValueOps;
    @Mock
    private QrService qrService;

    private UrlService urlService;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();
        appConfig.setBaseUrl("http://localhost:8080/");
        appConfig.setDefaultTtlHours(24);
        appConfig.setAliasLength(6);
        appConfig.setMaxAliasGenerationRetries(3);
        appConfig.setQrCacheTtlDays(7);

        lenient().when(redisTemplate.opsForValue()).thenReturn(stringValueOps);
        lenient().when(redisByteTemplate.opsForValue()).thenReturn(byteValueOps);

        UrlCacheService urlCacheService = new UrlCacheService(redisTemplate, redisByteTemplate);
        urlService = new UrlService(urlRepository, urlCacheService, qrService, appConfig);
    }

    @Test
    void createShortUrlOmitsQrByDefaultAndSkipsQrGeneration() {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("https://example.com/path");
        req.setCustomAlias("my-link");

        when(urlRepository.save(any(UrlDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenResponse res = urlService.createShortUrl(req, false);

        assertThat(res.getAlias()).isEqualTo("my-link");
        assertThat(res.getQrCode()).isNull();
        verify(qrService, never()).generateQr(anyString());
        verify(byteValueOps, never()).set(anyString(), any(), any());
    }

    @Test
    void createShortUrlWithIncludeQrUsesExpiresAtForUrlAndClampedQrCacheTtl() {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("https://example.com/path");
        req.setCustomAlias("my-link");
        req.setExpiresInDays(3);

        byte[] qrBytes = "fake-png".getBytes(StandardCharsets.UTF_8);
        when(urlRepository.save(any(UrlDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qrService.generateQr("http://localhost:8080/my-link")).thenReturn(qrBytes);

        ShortenResponse res = urlService.createShortUrl(req, true);

        assertThat(res.getAlias()).isEqualTo("my-link");
        assertThat(res.getShortUrl()).isEqualTo("http://localhost:8080/my-link");
        assertThat(res.isPermanent()).isFalse();
        assertThat(res.getQrCode()).isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(qrBytes));

        ArgumentCaptor<Duration> urlTtlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(stringValueOps).set(
                eq(CacheConstants.URL_CACHE_PREFIX + "my-link"),
                eq("0|https://example.com/path"),
                urlTtlCaptor.capture()
        );
        assertThat(urlTtlCaptor.getValue()).isGreaterThan(Duration.ofDays(2));
        assertThat(urlTtlCaptor.getValue()).isLessThanOrEqualTo(Duration.ofDays(3));

        ArgumentCaptor<Duration> qrTtlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(byteValueOps).set(eq(CacheConstants.QR_CACHE_PREFIX + "my-link"), eq(qrBytes), qrTtlCaptor.capture());
        assertThat(qrTtlCaptor.getValue()).isGreaterThan(Duration.ofDays(2));
        assertThat(qrTtlCaptor.getValue()).isLessThanOrEqualTo(Duration.ofDays(3));
    }

    @Test
    void createShortUrlRejectsSelfReferencingTargetUrl() {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("http://localhost:8080/some-alias");

        assertThatThrownBy(() -> urlService.createShortUrl(req, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot shorten URLs pointing to this service");
        verify(urlRepository, never()).save(any());
    }

    @Test
    void createShortUrlRejectsReservedAliasesIncludingUrls() {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("https://example.com");
        req.setCustomAlias("URLS");

        assertThatThrownBy(() -> urlService.createShortUrl(req))
                .isInstanceOf(ReservedAliasException.class);
        verify(urlRepository, never()).save(any());
    }

    @Test
    void createShortUrlReclaimsExpiredCustomAliasOnDuplicateKeyException() {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("https://example.com/new");
        req.setCustomAlias("reclaim");

        when(urlRepository.save(any(UrlDocument.class)))
                .thenThrow(new DuplicateKeyException("duplicate"))
                .thenAnswer(inv -> inv.getArgument(0));
        when(urlRepository.deactivateExpired(eq("reclaim"), any(Instant.class))).thenReturn(true);

        ShortenResponse res = urlService.createShortUrl(req, false);

        assertThat(res.getAlias()).isEqualTo("reclaim");
        verify(urlRepository, times(2)).save(any(UrlDocument.class));
    }

    @Test
    void createShortUrlTranslatesDuplicateKeyExceptionOnActiveCustomAliasToConflict() {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("https://example.com");
        req.setCustomAlias("raced");

        when(urlRepository.save(any(UrlDocument.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(urlRepository.deactivateExpired(eq("raced"), any(Instant.class))).thenReturn(false);

        assertThatThrownBy(() -> urlService.createShortUrl(req))
                .isInstanceOf(AliasAlreadyTakenException.class);
    }

    @Test
    void createShortUrlRetriesRandomAliasOnDuplicateKeyException() {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("https://example.com");

        when(urlRepository.save(any(UrlDocument.class)))
                .thenThrow(new DuplicateKeyException("collision"))
                .thenAnswer(inv -> inv.getArgument(0));

        ShortenResponse res = urlService.createShortUrl(req, false);
        assertThat(res.getAlias()).hasSize(6);
        verify(urlRepository, times(2)).save(any(UrlDocument.class));
    }

    @Test
    void createShortUrlThrowsWhenRandomAliasRetriesExhausted() {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("https://example.com");

        when(urlRepository.save(any(UrlDocument.class))).thenThrow(new DuplicateKeyException("collision"));

        assertThatThrownBy(() -> urlService.createShortUrl(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate unique alias");
    }

    @Test
    void getUrlInfoThrowsNotFoundWhenExpired() {
        UrlDocument expired = UrlDocument.builder()
                .alias("old")
                .originalUrl("https://example.com")
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .active(true)
                .build();
        when(urlRepository.findByAliasAndActiveTrue("old")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> urlService.getUrlInfo("old", true))
                .isInstanceOf(AliasNotFoundException.class);
        verify(qrService, never()).generateQr(anyString());
    }

    @Test
    void getUrlInfoReusesCachedQrBytesWhenIncludeQrIsTrue() {
        UrlDocument doc = UrlDocument.builder()
                .alias("abc123")
                .originalUrl("https://example.com")
                .createdAt(Instant.now())
                .active(true)
                .build();
        byte[] cachedQr = new byte[] {10, 20, 30};
        when(urlRepository.findByAliasAndActiveTrue("abc123")).thenReturn(Optional.of(doc));
        when(byteValueOps.get(CacheConstants.QR_CACHE_PREFIX + "abc123")).thenReturn(cachedQr);

        ShortenResponse res = urlService.getUrlInfo("abc123", true);

        assertThat(res.getQrCode()).isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(cachedQr));
        verify(qrService, never()).generateQr(anyString());
    }

    @Test
    void getQrCodeCacheMissGeneratesOnceWithoutCallingGetUrlInfoMapper() {
        UrlDocument doc = UrlDocument.builder()
                .alias("qr1")
                .originalUrl("https://example.com")
                .active(true)
                .build();
        byte[] png = new byte[] {7, 8, 9};
        when(byteValueOps.get(CacheConstants.QR_CACHE_PREFIX + "qr1")).thenReturn(null);
        when(urlRepository.findByAliasAndActiveTrue("qr1")).thenReturn(Optional.of(doc));
        when(qrService.generateQr("http://localhost:8080/qr1")).thenReturn(png);

        byte[] actual = urlService.getQrCode("qr1");

        assertThat(actual).isEqualTo(png);
        verify(qrService, times(1)).generateQr("http://localhost:8080/qr1");
        verify(byteValueOps).set(eq(CacheConstants.QR_CACHE_PREFIX + "qr1"), eq(png), eq(Duration.ofDays(7)));
    }

    @Test
    void deleteUrlAtomicallyDeactivatesAndEvictsBothRedisKeysTwice() {
        when(urlRepository.deactivateActive(eq("delme"), any(Instant.class))).thenReturn(true);

        urlService.deleteUrl("delme");

        verify(urlRepository).deactivateActive(eq("delme"), any(Instant.class));
        verify(redisTemplate, times(2)).delete(List.of(
                CacheConstants.URL_CACHE_PREFIX + "delme",
                CacheConstants.QR_CACHE_PREFIX + "delme"
        ));
    }

    @Test
    void deleteUrlThrowsNotFoundWhenNoActiveDocumentModified() {
        when(urlRepository.deactivateActive(eq("missing"), any(Instant.class))).thenReturn(false);

        assertThatThrownBy(() -> urlService.deleteUrl("missing"))
                .isInstanceOf(AliasNotFoundException.class);
    }
}
