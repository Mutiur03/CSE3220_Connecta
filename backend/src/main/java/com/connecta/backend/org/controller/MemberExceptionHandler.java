package com.connecta.backend.org.controller;

import com.connecta.backend.org.service.LastOwnerException;
import com.connecta.backend.org.service.MemberNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before GlobalExceptionHandler, whose catch-all would otherwise answer these with a 500. */
@RestControllerAdvice(basePackageClasses = MemberController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class MemberExceptionHandler {

    @ExceptionHandler(MemberNotFoundException.class)
    ProblemDetail notFound(MemberNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(LastOwnerException.class)
    ProblemDetail lastOwner(LastOwnerException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }
}
