package com.bolt.service;

import com.bolt.config.AppConfig;
import com.bolt.exception.QrGenerationException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrService
{
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private final AppConfig appConfig;

    public byte[] generateQr(String content)
    {
        if (!StringUtils.hasText(content))
        {
            throw new QrGenerationException("Failed to generate QR code");
        }
        try
        {
            int size = appConfig.getQrCodeSize();
            BitMatrix matrix = new QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                Map.of(
                    EncodeHintType.MARGIN, appConfig.getQrCodeMargin(),
                    EncodeHintType.CHARACTER_SET, "UTF-8"
                )
            );
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
            if (!ImageIO.write(image, "png", out))
            {
                throw new QrGenerationException("Failed to generate QR code");
            }
            byte[] result = out.toByteArray();
            log.debug("Generated QR code for content: {}, size: {} bytes",
                content.substring(0, Math.min(50, content.length())), result.length);
            return result;
        }
        catch (QrGenerationException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("Failed to generate QR code", e);
            throw new QrGenerationException("Failed to generate QR code");
        }
    }
}
