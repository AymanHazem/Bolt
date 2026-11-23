package com.bolt.util;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for common operations to reduce code duplication
 */
public final class UrlServiceUtil {

    private UrlServiceUtil() {
        // Prevent instantiation
    }

    /**
     * Current UTC timestamp truncated to millisecond precision (matching MongoDB BSON Date).
     */
    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * Calculate TTL duration based on expiration time or default TTL.
     * Returns {@link Duration#ZERO} when {@code expiresAt} is already past or present so callers skip caching.
     *
     * @param expiresAt the expiration instant (null for permanent)
     * @param defaultTtlHours default TTL in hours for permanent URLs
     * @return Duration for cache TTL
     */
    public static Duration calculateTtl(Instant expiresAt, long defaultTtlHours) {
        if (expiresAt != null) {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            return ttl.isNegative() || ttl.isZero() ? Duration.ZERO : ttl;
        }
        return Duration.ofHours(defaultTtlHours);
    }

    /**
     * Generate a random alias efficiently using ThreadLocalRandom.
     * Much faster than stream-based approach.
     *
     * @param length the desired alias length
     * @return random alias string
     */
    public static String generateRandomAlias(int length) {
        char[] chars = new char[length];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String charset = CacheConstants.ALIAS_CHARSET;

        for (int i = 0; i < length; i++) {
            chars[i] = charset.charAt(random.nextInt(charset.length()));
        }
        return new String(chars);
    }

    /**
     * Check if instant is expired
     */
    public static boolean isExpired(Instant expiresAt) {
        return expiresAt != null && !Instant.now().isBefore(expiresAt);
    }
}

