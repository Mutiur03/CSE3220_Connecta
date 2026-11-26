package com.connecta.backend.org.controller;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.org.service.InvalidReportRangeException;
import com.connecta.backend.org.service.OrganizationSlugTakenException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before GlobalExceptionHandler, whose catch-all would otherwise answer these with a 500. */
@RestControllerAdvice(basePackageClasses = OrganizationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class OrganizationExceptionHandler {

    @ExceptionHandler(OrganizationSlugTakenException.class)
    ProblemDetail slugTaken(OrganizationSlugTakenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AccountNotAvailableException.class)
    ProblemDetail accountNotAvailable(AccountNotAvailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(InvalidReportRangeException.class)
    ProblemDetail invalidReportRange(InvalidReportRangeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
