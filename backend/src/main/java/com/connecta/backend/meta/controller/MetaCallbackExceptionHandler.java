package com.connecta.backend.meta.controller;

import com.connecta.backend.config.FrontendProperties;
import com.connecta.backend.meta.service.InvalidOAuthStateException;
import com.connecta.backend.meta.service.MetaAuthorizationDeniedException;
import com.connecta.backend.meta.service.MetaAuthorizationFailedException;
import com.connecta.backend.meta.service.MetaNotConfiguredException;
import com.connecta.backend.meta.service.OAuthStateSigner;
import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.org.service.NotAMemberException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The callbacks are opened by the browser, so a failure must not show a JSON body. Each failure sends the admin
 * back to the Integrations page with an error code the page shows as a message. The organization comes from the
 * state, read without checking its signature: it only chooses which page to return to, and the page enforces access.
 */
@RestControllerAdvice(assignableTypes = {MetaOAuthCallbackController.class, InstagramOAuthCallbackController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class MetaCallbackExceptionHandler {

    private static final String STATE = "state";

    private final FrontendProperties frontend;
    private final OAuthStateSigner states;

    @ExceptionHandler(InvalidOAuthStateException.class)
    ResponseEntity<Void> invalidState(HttpServletRequest request) {
        return back(request, "invalid_state");
    }

    @ExceptionHandler(MetaAuthorizationDeniedException.class)
    ResponseEntity<Void> denied(HttpServletRequest request) {
        return back(request, "denied");
    }

    @ExceptionHandler({InsufficientOrganizationRoleException.class, NotAMemberException.class})
    ResponseEntity<Void> forbidden(HttpServletRequest request) {
        return back(request, "forbidden");
    }

    @ExceptionHandler(MetaNotConfiguredException.class)
    ResponseEntity<Void> notConfigured(HttpServletRequest request) {
        return back(request, "not_configured");
    }

    @ExceptionHandler(MetaAuthorizationFailedException.class)
    ResponseEntity<Void> authorizationFailed(HttpServletRequest request) {
        return back(request, "meta_failed");
    }

    private ResponseEntity<Void> back(HttpServletRequest request, String errorCode) {
        String base = frontend.baseUrl();
        String location = states.organizationIdOf(request.getParameter(STATE))
                .map(organizationId -> MetaCallbackRedirects.integrations(base, organizationId, "metaError=" + errorCode))
                .orElseGet(() -> MetaCallbackRedirects.organizations(base, errorCode));
        return MetaCallbackRedirects.to(location);
    }
}
