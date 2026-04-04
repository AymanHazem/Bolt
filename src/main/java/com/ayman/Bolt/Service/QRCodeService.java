package com.ayman.Bolt.Service;

import com.ayman.Bolt.Model.DTOs.QRcodeReq;
import com.google.zxing.EncodeHintType;
import net.glxn.qrgen.javase.QRCode;
import org.springframework.stereotype.Service;

@Service
public class QRCodeService
{
    public byte[]getQR (QRcodeReq qrcodeReq)
    {
        return QRCode.from(qrcodeReq.getUrl())
                .withSize(300,300)
                .withHint(EncodeHintType.MARGIN, 1)
                .stream()
                .toByteArray();
    }
}
