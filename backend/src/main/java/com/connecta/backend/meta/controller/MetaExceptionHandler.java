package com.connecta.backend.meta.controller;

import com.connecta.backend.meta.service.InvalidOAuthStateException;
import com.connecta.backend.meta.service.MetaAuthorizationFailedException;
import com.connecta.backend.meta.service.MetaNotConfiguredException;
import com.connecta.backend.meta.service.MetaPageNotAllowedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the Meta connection failures to problem details. The bodies are generic: Meta's error text and any token
 * never reach the client; the adapter logs what it needs. Ordered first so it wins over the catch-all in
 * GlobalExceptionHandler for these types.
 */
@RestControllerAdvice(assignableTypes = {
    MetaAuthorizationController.class, MetaOAuthCallbackController.class, MetaPageController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
class MetaExceptionHandler {

    @ExceptionHandler(MetaNotConfiguredException.class)
    ProblemDetail notConfigured(MetaNotConfiguredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(InvalidOAuthStateException.class)
    ProblemDetail invalidState(InvalidOAuthStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MetaAuthorizationFailedException.class)
    ProblemDetail authorizationFailed(MetaAuthorizationFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Meta could not complete the authorization. Try again.");
    }

    @ExceptionHandler(MetaPageNotAllowedException.class)
    ProblemDetail pageNotAllowed(MetaPageNotAllowedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }
}
