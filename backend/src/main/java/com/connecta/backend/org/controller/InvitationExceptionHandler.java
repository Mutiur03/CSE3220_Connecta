package com.connecta.backend.org.controller;

import com.connecta.backend.org.domain.InvitationAlreadyAcceptedException;
import com.connecta.backend.org.domain.InvitationNotAcceptableException;
import com.connecta.backend.org.service.AlreadyMemberException;
import com.connecta.backend.org.service.DuplicateInvitationException;
import com.connecta.backend.org.service.InvitationNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before GlobalExceptionHandler, whose catch-all would otherwise answer these with a 500. */
@RestControllerAdvice(basePackageClasses = InvitationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class InvitationExceptionHandler {

    @ExceptionHandler(InvitationNotFoundException.class)
    ProblemDetail notFound(InvitationNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({DuplicateInvitationException.class, AlreadyMemberException.class,
        InvitationAlreadyAcceptedException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvitationNotAcceptableException.class)
    ProblemDetail notAcceptable(InvitationNotAcceptableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }
}
