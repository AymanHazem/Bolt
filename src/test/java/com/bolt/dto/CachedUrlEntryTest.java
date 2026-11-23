package com.bolt.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CachedUrlEntryTest {

    @Test
    void encodeAndDecodeRoundTripPermanentAndExpiringEntries() {
        CachedUrlEntry permanent = new CachedUrlEntry("https://example.com/a|b", true);
        CachedUrlEntry expiring = new CachedUrlEntry("https://example.com/temp", false);

        assertThat(permanent.encode()).isEqualTo("1|https://example.com/a|b");
        assertThat(CachedUrlEntry.decode(permanent.encode())).isEqualTo(permanent);

        assertThat(expiring.encode()).isEqualTo("0|https://example.com/temp");
        assertThat(CachedUrlEntry.decode(expiring.encode())).isEqualTo(expiring);
    }

    @Test
    void decodeReturnsNullForLegacyJsonNullOrMalformedValues() {
        assertThat(CachedUrlEntry.decode(null)).isNull();
        assertThat(CachedUrlEntry.decode("")).isNull();
        assertThat(CachedUrlEntry.decode("1|")).isNull();
        assertThat(CachedUrlEntry.decode("2|https://example.com")).isNull();
        assertThat(CachedUrlEntry.decode("{\"originalUrl\":\"https://example.com\",\"permanent\":true}")).isNull();
    }
}
