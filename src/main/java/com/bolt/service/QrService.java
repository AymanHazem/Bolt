package com.bolt.service;
import java.io.ByteArrayOutputStream;
import com.google.zxing.EncodeHintType;
import net.glxn.qrgen.javase.QRCode;
import org.springframework.stereotype.Service;
@Service
public class QrService
{
    public byte[] generateQr(String content)
    {
        ByteArrayOutputStream stream = QRCode
                .from(content)
                .withSize(300, 300)
                .withHint(EncodeHintType.MARGIN, 1)
                .stream();
        return stream.toByteArray();
    }
}
