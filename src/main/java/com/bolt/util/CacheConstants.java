package com.bolt.util;

/**
 * Application-wide constants to avoid duplication and magic numbers
 */
public final class CacheConstants {
    public static final String URL_CACHE_PREFIX = "url:";
    public static final String QR_CACHE_PREFIX = "qr:";
    
    // Reserved aliases that cannot be used
    public static final java.util.Set<String> RESERVED_ALIASES = java.util.Set.of(
            "api", "admin", "health", "login", "register",
            "docs", "static", "assets", "favicon", "qr", "info"
    );
    
    // Charset for generating random aliases
    public static final String ALIAS_CHARSET = 
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    
    private CacheConstants() {
        // Prevent instantiation
    }
}

