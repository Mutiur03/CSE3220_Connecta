package com.connecta.backend.setup.controller;

import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.setup.service.SetupAlreadyCompleteException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before GlobalExceptionHandler, whose catch-all would otherwise answer these with a 500. */
@RestControllerAdvice(basePackageClasses = SetupController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class SetupExceptionHandler {

    @ExceptionHandler({SetupAlreadyCompleteException.class, EmailAlreadyUsedException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
