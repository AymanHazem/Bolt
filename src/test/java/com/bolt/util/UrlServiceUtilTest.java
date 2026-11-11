package com.bolt.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UrlServiceUtilTest {

    @Test
    void calculateTtlUsesDefaultWhenExpiresAtNull() {
        Duration ttl = UrlServiceUtil.calculateTtl(null, 24);
        assertThat(ttl).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void calculateTtlClampsExpiredTimestampToOneSecond() {
        Duration ttl = UrlServiceUtil.calculateTtl(LocalDateTime.now().minusMinutes(5), 24);
        assertThat(ttl).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void calculateTtlUsesRemainingTimeForFutureExpiry() {
        Duration ttl = UrlServiceUtil.calculateTtl(LocalDateTime.now().plusHours(48), 24);
        assertThat(ttl).isGreaterThan(Duration.ofHours(47));
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofHours(48));
    }

    @Test
    void generateRandomAliasUsesConfiguredLengthAndCharset() {
        String alias = UrlServiceUtil.generateRandomAlias(8);
        assertThat(alias).hasSize(8);
        for (char c : alias.toCharArray()) {
            assertThat(CacheConstants.ALIAS_CHARSET).contains(String.valueOf(c));
        }
    }

    @Test
    void isExpiredDetectsPastAndFutureTimestamps() {
        assertThat(UrlServiceUtil.isExpired(null)).isFalse();
        assertThat(UrlServiceUtil.isExpired(LocalDateTime.now().plusMinutes(10))).isFalse();
        assertThat(UrlServiceUtil.isExpired(LocalDateTime.now().minusSeconds(1))).isTrue();
    }
}
