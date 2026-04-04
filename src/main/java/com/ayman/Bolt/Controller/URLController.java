package com.ayman.Bolt.Controller;

import com.ayman.Bolt.Model.DTOs.QRcodeReq;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/short")
public class URLController
{
    @PostMapping(value = "/qr",produces = MediaType.IMAGE_PNG_VALUE)
    public byte[]getQR (@RequestBody QRcodeReq qrcodeReq)
    {
        return null;
    }
}
