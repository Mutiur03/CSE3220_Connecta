package com.connecta.backend.org.controller;

import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.org.service.NotAMemberException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tenant failures for every controller, because any feature's service may raise them: 404 for a non-member
 * (same body as an unknown organization), 403 for a member whose role is too low. Ordered first so it wins
 * over the catch-all in GlobalExceptionHandler for these two types.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class TenantExceptionHandler {

    @ExceptionHandler(NotAMemberException.class)
    ProblemDetail notAMember(NotAMemberException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InsufficientOrganizationRoleException.class)
    ProblemDetail insufficientRole(InsufficientOrganizationRoleException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }
}
