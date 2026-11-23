package com.bolt.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void setBaseUrlStripsTrailingSlashesAndWhitespace() {
        AppConfig config = new AppConfig();

        config.setBaseUrl("  https://bolt.example.com///  ");
        assertThat(config.getBaseUrl()).isEqualTo("https://bolt.example.com");
        assertThat(validator.validate(config)).isEmpty();

        config.setBaseUrl("http://localhost:8080/");
        assertThat(config.getBaseUrl()).isEqualTo("http://localhost:8080");

        config.setBaseUrl(null);
        assertThat(config.getBaseUrl()).isNull();
    }

    @Test
    void validationRejectsBlankBaseUrlOrNonPositiveBounds() {
        AppConfig invalidBase = new AppConfig();
        invalidBase.setBaseUrl("   ");
        assertThat(validator.validate(invalidBase)).isNotEmpty();

        AppConfig invalidAliasLen = new AppConfig();
        invalidAliasLen.setAliasLength(0);
        assertThat(validator.validate(invalidAliasLen)).isNotEmpty();
    }
}
