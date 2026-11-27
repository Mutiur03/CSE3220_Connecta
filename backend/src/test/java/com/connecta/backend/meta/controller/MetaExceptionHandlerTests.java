package com.connecta.backend.meta.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.connecta.backend.meta.service.InvalidOAuthStateException;
import com.connecta.backend.meta.service.InvalidWebhookSignatureException;
import com.connecta.backend.meta.service.MalformedWebhookException;
import com.connecta.backend.meta.service.MetaAuthorizationFailedException;
import com.connecta.backend.meta.service.MetaNotConfiguredException;
import com.connecta.backend.meta.service.MetaPageNotAllowedException;
import com.connecta.backend.meta.service.WebhookVerificationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Each Meta failure maps to its own status; the body never carries the upstream error text. */
class MetaExceptionHandlerTests {

    private final MetaExceptionHandler handler = new MetaExceptionHandler();

    @Test
    void metaNotConfiguredIs503() {
        assertStatus(handler.notConfigured(new MetaNotConfiguredException()), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void anInvalidOAuthStateIs400() {
        assertStatus(handler.invalidState(new InvalidOAuthStateException()), HttpStatus.BAD_REQUEST);
    }

    @Test
    void aFailedAuthorizationIs502WithAGenericDetail() {
        ProblemDetail problem = handler.authorizationFailed(
                new MetaAuthorizationFailedException("token endpoint said secret-detail"));

        assertStatus(problem, HttpStatus.BAD_GATEWAY);
        assertThat(problem.getDetail()).doesNotContain("secret-detail");
    }

    @Test
    void aPageOutsideTheGrantIs422() {
        assertStatus(handler.pageNotAllowed(new MetaPageNotAllowedException()), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void aBadWebhookSignatureIs401() {
        assertStatus(handler.invalidSignature(new InvalidWebhookSignatureException()), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aMalformedWebhookIs400() {
        assertStatus(handler.malformedWebhook(new MalformedWebhookException()), HttpStatus.BAD_REQUEST);
    }

    @Test
    void aFailedWebhookVerificationIs403() {
        assertStatus(handler.verificationFailed(new WebhookVerificationFailedException()), HttpStatus.FORBIDDEN);
    }

    private static void assertStatus(ProblemDetail problem, HttpStatus expected) {
        assertThat(problem.getStatus()).isEqualTo(expected.value());
        assertThat(problem.getDetail()).isNotBlank();
    }
}
