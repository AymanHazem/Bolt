package com.bolt.controller;
import com.bolt.dto.CachedUrlEntry;
import com.bolt.service.RedirectService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
@Slf4j
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
        log.info("Redirecting alias {} to {} with status {}", alias, 
            entry.originalUrl().substring(0, Math.min(50, entry.originalUrl().length())), status);
        return ResponseEntity.status(status).location(URI.create(entry.originalUrl())).build();
    }
}
