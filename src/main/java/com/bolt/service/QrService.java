package com.bolt.service;
import com.bolt.config.AppConfig;
import com.bolt.exception.QrGenerationException;
import com.google.zxing.EncodeHintType;
import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.glxn.qrgen.javase.QRCode;
import org.springframework.stereotype.Service;
@Slf4j
@Service
@RequiredArgsConstructor
public class QrService
{
    private final AppConfig appConfig;
    public byte[] generateQr(String content)
    {
        try
        {
            ByteArrayOutputStream stream = QRCode
                .from(content)
                .withSize(appConfig.getQrCodeSize(), appConfig.getQrCodeSize())
                .withHint(EncodeHintType.MARGIN, appConfig.getQrCodeMargin())
                .stream();
            byte[] result = stream.toByteArray();
            log.debug("Generated QR code for content: {}, size: {} bytes", 
                content.substring(0, Math.min(50, content.length())), result.length);
            return result;
        }
        catch (Exception e)
        {
            log.error("Failed to generate QR code for content: {}", content, e);
            throw new QrGenerationException("Failed to generate QR code: " + e.getMessage());
        }
    }
}
