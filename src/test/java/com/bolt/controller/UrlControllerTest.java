package com.bolt.controller;

import com.bolt.dto.ShortenResponse;
import com.bolt.exception.*;
import com.bolt.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UrlControllerTest {

    @Mock
    private UrlService urlService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UrlController(urlService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createShortUrlReturns201WithLocationHeaderAndForwardsIncludeQr() throws Exception {
        ShortenResponse res = ShortenResponse.builder()
                .alias("abc123")
                .shortUrl("http://localhost:8080/abc123")
                .originalUrl("https://example.com")
                .createdAt(Instant.now())
                .permanent(true)
                .build();
        when(urlService.createShortUrl(any(), eq(false))).thenReturn(res);

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"abc123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/abc123"))
                .andExpect(jsonPath("$.alias").value("abc123"))
                .andExpect(jsonPath("$.qrCode").doesNotExist());
    }

    @Test
    void reservedAliasReturnsStandard400ErrorShape() throws Exception {
        when(urlService.createShortUrl(any(), eq(false))).thenThrow(new ReservedAliasException("urls"));

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"urls\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVED_ALIAS"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void duplicateAliasReturnsStandard409ErrorShape() throws Exception {
        when(urlService.createShortUrl(any(), eq(false))).thenThrow(new AliasAlreadyTakenException("taken"));

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedJsonUnsupportedMethodAndUnsupportedMediaTypeUseStandardErrorShape() throws Exception {
        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(put("/urls/abc123"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unexpectedRuntimeIllegalStateAndQrErrorReturnSanitized500() throws Exception {
        when(urlService.getUrlInfo("boom12", false)).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/urls/boom12"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"));

        when(urlService.getQrCode("qrboom")).thenThrow(new QrGenerationException("internal detail"));

        mockMvc.perform(get("/urls/qrboom/qr"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("QR_ERROR"))
                .andExpect(jsonPath("$.message").value("Failed to generate QR code"));
    }

    @Test
    void getQrCodeIncludesCacheHeaderAndDeleteUrlWorksAsExpected() throws Exception {
        byte[] png = new byte[] {1, 2, 3};
        when(urlService.getQrCode("abc123")).thenReturn(png);

        mockMvc.perform(get("/urls/abc123/qr"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andExpect(content().bytes(png));

        doThrow(new AliasNotFoundException("missing")).when(urlService).deleteUrl("missing");

        mockMvc.perform(delete("/urls/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void validationErrorIncludesFieldNamesAndInvalidUriReturns400() throws Exception {
        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"\",\"customAlias\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("originalUrl:")))
                .andExpect(jsonPath("$.timestamp").exists());

        when(urlService.createShortUrl(any(), eq(false))).thenThrow(new IllegalArgumentException("Illegal character in path"));

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }
}
