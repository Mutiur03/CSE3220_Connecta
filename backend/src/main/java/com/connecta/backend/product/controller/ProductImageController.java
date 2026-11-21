package com.connecta.backend.product.controller;

import com.connecta.backend.product.dto.ProductImageResponse;
import com.connecta.backend.product.service.ProductImageContent;
import com.connecta.backend.product.service.ProductImageService;
import com.connecta.backend.product.service.ProductImageStorageException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Role rules (checked by ProductImageService through TenantAccessService). Listing and content need any membership
 * of the organization. Upload and delete need ADMIN of it. A non-member or another tenant's id is 404.
 */
@RestController
@Tag(name = "Product images", description = "Images of a product")
@RequiredArgsConstructor
public class ProductImageController {

    private static final String IMAGES_PATH = "/api/organizations/{organizationId}/products/{productId}/images";

    private final ProductImageService images;

    @Operation(summary = "Upload an image, multipart field 'file' (ADMIN of the organization)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "201", description = "Image stored; Location points to its content")
    @ApiResponse(responseCode = "400", description = "The file part is missing")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT of the organization")
    @ApiResponse(responseCode = "404", description = "Product not found, or caller not a member")
    @ApiResponse(responseCode = "413", description = "The file is larger than the limit")
    @ApiResponse(responseCode = "415", description = "Not a JPEG, PNG or WebP image")
    @ApiResponse(responseCode = "422", description = "The product already has the maximum number of images")
    @PostMapping(value = IMAGES_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ProductImageResponse> upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID productId, @RequestParam("file") MultipartFile file) {
        ProductImageResponse created = images.upload(jwt.getSubject(), organizationId, productId,
                file.getContentType(), bytesOf(file));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}/content").buildAndExpand(created.id()).toUri()).body(created);
    }

    @Operation(summary = "List a product's images in display order (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The images")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Product not found, or caller not a member")
    @GetMapping(IMAGES_PATH)
    List<ProductImageResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID productId) {
        return images.list(jwt.getSubject(), organizationId, productId);
    }

    @Operation(summary = "Stream an image's bytes (any member)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "The image bytes with their stored content type")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "404", description = "Image not found, another organization's, or caller not a member")
    @GetMapping(IMAGES_PATH + "/{imageId}/content")
    ResponseEntity<byte[]> content(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId,
            @PathVariable UUID productId, @PathVariable UUID imageId) {
        ProductImageContent image = images.content(jwt.getSubject(), organizationId, productId, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .cacheControl(CacheControl.empty().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    @Operation(summary = "Delete an image (ADMIN of the organization)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "Image deleted")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token")
    @ApiResponse(responseCode = "403", description = "Caller is an AGENT of the organization")
    @ApiResponse(responseCode = "404", description = "Image not found, another organization's, or caller not a member")
    @DeleteMapping(IMAGES_PATH + "/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID organizationId, @PathVariable UUID productId,
            @PathVariable UUID imageId) {
        images.delete(jwt.getSubject(), organizationId, productId, imageId);
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ProductImageStorageException(e);
        }
    }
}
