package com.bolt.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortenRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void rejectsEmptyBlankNonHttpAndOversizedUrls() {
        ShortenRequest empty = new ShortenRequest();
        empty.setOriginalUrl("");
        assertThat(validator.validate(empty)).isNotEmpty();

        ShortenRequest blank = new ShortenRequest();
        blank.setOriginalUrl("   ");
        assertThat(validator.validate(blank)).isNotEmpty();

        ShortenRequest ftp = new ShortenRequest();
        ftp.setOriginalUrl("ftp://example.com/file");
        assertThat(validator.validate(ftp)).isNotEmpty();

        ShortenRequest file = new ShortenRequest();
        file.setOriginalUrl("file:///etc/passwd");
        assertThat(validator.validate(file)).isNotEmpty();

        ShortenRequest js = new ShortenRequest();
        js.setOriginalUrl("javascript:alert(1)");
        assertThat(validator.validate(js)).isNotEmpty();

        ShortenRequest oversized = new ShortenRequest();
        oversized.setOriginalUrl("https://example.com/" + "a".repeat(2040));
        assertThat(validator.validate(oversized)).isNotEmpty();
    }

    @Test
    void acceptsValidHttpAndHttpsUrls() {
        ShortenRequest https = new ShortenRequest();
        https.setOriginalUrl("https://example.com/path?q=1");
        assertThat(validator.validate(https)).isEmpty();

        ShortenRequest http = new ShortenRequest();
        http.setOriginalUrl("http://example.com:8080/test");
        assertThat(validator.validate(http)).isEmpty();
    }
}
