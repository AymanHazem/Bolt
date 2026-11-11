package com.bolt.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bolt.dto.CachedUrlEntry;
import com.bolt.exception.AliasNotFoundException;
import com.bolt.exception.GlobalExceptionHandler;
import com.bolt.service.RedirectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RedirectControllerTest {

    @Mock
    private RedirectService redirectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RedirectController(redirectService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void permanentUrlReturns301AndExpiringReturns302() throws Exception {
        when(redirectService.resolveUrl("perm"))
                .thenReturn(new CachedUrlEntry("https://example.com/perm", true));
        when(redirectService.resolveUrl("temp"))
                .thenReturn(new CachedUrlEntry("https://example.com/temp", false));

        mockMvc.perform(get("/perm"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://example.com/perm"));

        mockMvc.perform(get("/temp"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/temp"));
    }

    @Test
    void unknownAliasReturns404ErrorResponse() throws Exception {
        when(redirectService.resolveUrl("missing")).thenThrow(new AliasNotFoundException("missing"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
