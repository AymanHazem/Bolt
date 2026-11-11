package com.bolt.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolt.dto.ShortenResponse;
import com.bolt.exception.AliasAlreadyTakenException;
import com.bolt.exception.AliasNotFoundException;
import com.bolt.exception.GlobalExceptionHandler;
import com.bolt.exception.ReservedAliasException;
import com.bolt.service.UrlService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
    void createShortUrlReturns201WithLocationHeader() throws Exception {
        ShortenResponse res = ShortenResponse.builder()
                .alias("abc123")
                .shortUrl("http://localhost:8080/abc123")
                .qrCode("data:image/png;base64,ZmFrZQ==")
                .originalUrl("https://example.com")
                .createdAt(LocalDateTime.now())
                .permanent(true)
                .build();
        when(urlService.createShortUrl(any())).thenReturn(res);

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"abc123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/abc123"))
                .andExpect(jsonPath("$.alias").value("abc123"));
    }

    @Test
    void reservedAliasReturnsStandard400ErrorShape() throws Exception {
        when(urlService.createShortUrl(any())).thenThrow(new ReservedAliasException("urls"));

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"urls\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVED_ALIAS"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void duplicateAliasReturnsStandard409ErrorShape() throws Exception {
        when(urlService.createShortUrl(any())).thenThrow(new AliasAlreadyTakenException("taken"));

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"customAlias\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedJsonAndUnsupportedMethodUseStandardErrorShape() throws Exception {
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
    }

    @Test
    void unexpectedRuntimeAndIllegalStateReturnSanitized500() throws Exception {
        when(urlService.getUrlInfo("boom")).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/urls/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void getQrCodeAndDeleteUrlWorkAsExpected() throws Exception {
        byte[] png = new byte[] {1, 2, 3};
        when(urlService.getQrCode("abc123")).thenReturn(png);

        mockMvc.perform(get("/urls/abc123/qr"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(png));

        doThrow(new AliasNotFoundException("missing")).when(urlService).deleteUrl("missing");

        mockMvc.perform(delete("/urls/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void validationErrorAndInvalidUriReturn400WithStandardErrorShape() throws Exception {
        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"not-a-url\",\"customAlias\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").exists());

        when(urlService.createShortUrl(any())).thenThrow(new IllegalArgumentException("Illegal character in path"));

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/ok\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }
}
