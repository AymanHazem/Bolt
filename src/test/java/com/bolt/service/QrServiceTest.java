package com.bolt.service;

import com.bolt.config.AppConfig;
import com.bolt.exception.QrGenerationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrServiceTest {

    @Test
    void generateQrProducesValidPngBytes() {
        AppConfig config = new AppConfig();
        config.setQrCodeSize(200);
        config.setQrCodeMargin(1);
        QrService qrService = new QrService(config);

        byte[] png = qrService.generateQr("http://localhost:8080/abc123");

        assertThat(png.length).isGreaterThan(8);
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');
    }

    @Test
    void generateQrThrowsQrGenerationExceptionOnInvalidInput() {
        AppConfig config = new AppConfig();
        QrService qrService = new QrService(config);

        assertThatThrownBy(() -> qrService.generateQr(null))
                .isInstanceOf(QrGenerationException.class)
                .hasMessageContaining("Failed to generate QR code");
    }
}
