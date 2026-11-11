package com.bolt.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void setBaseUrlStripsTrailingSlashesAndWhitespace() {
        AppConfig config = new AppConfig();

        config.setBaseUrl("  https://bolt.example.com///  ");
        assertThat(config.getBaseUrl()).isEqualTo("https://bolt.example.com");

        config.setBaseUrl("http://localhost:8080/");
        assertThat(config.getBaseUrl()).isEqualTo("http://localhost:8080");

        config.setBaseUrl(null);
        assertThat(config.getBaseUrl()).isNull();
    }
}
