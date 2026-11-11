package com.bolt.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for common operations to reduce code duplication
 */
public final class UrlServiceUtil {

    private UrlServiceUtil() {
        // Prevent instantiation
    }

    /**
     * Calculate TTL duration based on expiration time or default TTL.
     * More efficient than duplicating this logic in multiple services.
     *
     * @param expiresAt the expiration datetime (null for permanent)
     * @param defaultTtlHours default TTL in hours for permanent URLs
     * @return Duration for cache TTL
     */
    public static Duration calculateTtl(LocalDateTime expiresAt, long defaultTtlHours) {
        if (expiresAt != null) {
            Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);
            // Ensure minimum 1 second TTL if already expired or zero
            return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
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
     * Check if datetime is expired or will expire soon
     */
    public static boolean isExpired(LocalDateTime expiresAt) {
        return expiresAt != null && !LocalDateTime.now().isBefore(expiresAt);
    }
}

