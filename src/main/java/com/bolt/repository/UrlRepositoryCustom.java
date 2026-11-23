package com.bolt.repository;

import java.time.Instant;

public interface UrlRepositoryCustom {

    /**
     * Atomically deactivates an active, non-expired document for the given alias.
     */
    boolean deactivateActive(String alias, Instant now);

    /**
     * Atomically deactivates an active document that has already passed {@code expiresAt}
     * (freeing the partial unique index before MongoDB's background TTL monitor runs).
     */
    boolean deactivateExpired(String alias, Instant now);
}
