package com.bolt.dto;

public record CachedUrlEntry(String originalUrl, boolean permanent) {

    public String encode() {
        return (permanent ? "1|" : "0|") + originalUrl;
    }

    /**
     * Decodes a compact {@code "<0|1>|<url>"} entry, returning {@code null} for legacy/unreadable formats.
     */
    public static CachedUrlEntry decode(String raw) {
        if (raw == null || raw.length() < 3 || raw.charAt(1) != '|') {
            return null;
        }
        char flag = raw.charAt(0);
        if (flag != '0' && flag != '1') {
            return null;
        }
        return new CachedUrlEntry(raw.substring(2), flag == '1');
    }
}
