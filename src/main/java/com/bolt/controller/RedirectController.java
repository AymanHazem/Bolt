package com.bolt.controller;
import com.bolt.dto.CachedUrlEntry;
import com.bolt.service.RedirectService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequiredArgsConstructor
public class RedirectController
{
    private final RedirectService redirectService;
    @GetMapping("/{alias}")
    public ResponseEntity<Void> redirect(@PathVariable String alias)
    {
        CachedUrlEntry entry = redirectService.resolveUrl(alias);
        HttpStatus status = entry.permanent() ? HttpStatus.MOVED_PERMANENTLY : HttpStatus.FOUND;
        return ResponseEntity.status(status).location(URI.create(entry.originalUrl())).build();
    }
}
