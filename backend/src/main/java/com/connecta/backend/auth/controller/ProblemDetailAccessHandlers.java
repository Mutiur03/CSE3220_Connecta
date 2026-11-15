package com.connecta.backend.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * JSON error bodies (RFC 9457 problem details) for security failures, in the same shape the
 * controllers use. 401 keeps the standard {@code WWW-Authenticate: Bearer ...} header, which
 * says {@code invalid_token} for an expired or tampered token.
 */
public class ProblemDetailAccessHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        bearer.commence(request, response, e);
        write(response, HttpStatus.UNAUTHORIZED, "Authentication is required or the token is invalid or expired");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    private void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        converter.write(ProblemDetail.forStatusAndDetail(status, detail),
                MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
    }
}
