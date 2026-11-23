package com.bolt.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UrlServiceUtilTest {

    @Test
    void calculateTtlUsesDefaultWhenExpiresAtNull() {
        Duration ttl = UrlServiceUtil.calculateTtl(null, 24);
        assertThat(ttl).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void calculateTtlReturnsZeroForExpiredTimestamp() {
        Duration ttl = UrlServiceUtil.calculateTtl(Instant.now().minus(5, ChronoUnit.MINUTES), 24);
        assertThat(ttl).isEqualTo(Duration.ZERO);
    }

    @Test
    void calculateTtlUsesRemainingTimeForFutureExpiry() {
        Duration ttl = UrlServiceUtil.calculateTtl(Instant.now().plus(48, ChronoUnit.HOURS), 24);
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
        assertThat(UrlServiceUtil.isExpired(Instant.now().plus(10, ChronoUnit.MINUTES))).isFalse();
        assertThat(UrlServiceUtil.isExpired(Instant.now().minusSeconds(1))).isTrue();
    }
}
