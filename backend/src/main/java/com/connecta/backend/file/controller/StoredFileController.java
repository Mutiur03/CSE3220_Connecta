package com.connecta.backend.file.controller;

import com.connecta.backend.file.service.StoredFileContent;
import com.connecta.backend.file.service.StoredFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role rule: public on purpose (permit-all in SecurityConfig), because Meta fetches these files without a token.
 * The id is a random UUID, so a file can only be read by whoever was given its link.
 */
@RestController
@Tag(name = "Files", description = "Public read of stored files")
@RequiredArgsConstructor
public class StoredFileController {

    /** A stored file never changes, so clients may keep it; private keeps shared caches out. */
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable();

    private final StoredFileService files;

    @Operation(summary = "Read a stored file (public, the id is the check)")
    @ApiResponse(responseCode = "200", description = "The bytes with their stored content type")
    @ApiResponse(responseCode = "404", description = "No file has this id")
    @GetMapping("/api/files/{id}")
    ResponseEntity<byte[]> read(@PathVariable UUID id) {
        StoredFileContent file = files.load(id);
        ContentDisposition.Builder disposition = ContentDisposition.inline();
        if (file.fileName() != null) {
            disposition.filename(file.fileName(), StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CACHE)
                .body(file.bytes());
    }
}
