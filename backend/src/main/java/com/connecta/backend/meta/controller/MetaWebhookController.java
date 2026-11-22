package com.connecta.backend.meta.controller;

import com.connecta.backend.meta.service.WebhookIntakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta's webhook callback. Meta calls it without a token, so both routes are public in SecurityConfig: the GET
 * is checked against the verify token, the POST against the event's signature. Nothing here reads tenant data.
 */
@RestController
@Tag(name = "Meta webhooks", description = "Meta's callback for Messenger and Instagram events")
@RequestMapping("/api/meta/webhooks")
@RequiredArgsConstructor
class MetaWebhookController {

    private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";

    private final WebhookIntakeService intake;

    @Operation(summary = "Meta's subscription check: returns hub.challenge when the verify token matches")
    @ApiResponse(responseCode = "200", description = "The challenge, as plain text")
    @ApiResponse(responseCode = "400", description = "A required hub parameter is missing")
    @ApiResponse(responseCode = "403", description = "Wrong mode or verify token, or none configured")
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    String confirm(@RequestParam("hub.mode") String mode, @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        return intake.confirmSubscription(mode, verifyToken, challenge);
    }

    @Operation(summary = "Receive an event: verifies X-Hub-Signature-256 over the raw body, then stores it")
    @ApiResponse(responseCode = "200", description = "Received (also for products this API does not store)")
    @ApiResponse(responseCode = "400", description = "The body is not valid JSON")
    @ApiResponse(responseCode = "401", description = "The signature is missing or does not match")
    @ApiResponse(responseCode = "503", description = "The Meta app secret for this product is not configured")
    @PostMapping
    ResponseEntity<Void> receive(@RequestBody String rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        intake.receive(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
