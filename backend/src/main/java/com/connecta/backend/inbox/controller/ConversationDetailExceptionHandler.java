package com.connecta.backend.inbox.controller;

import com.connecta.backend.inbox.service.ConversationNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Conversation failures as problem details. Runs before GlobalExceptionHandler, whose catch-all would give a 500. */
@RestControllerAdvice(basePackageClasses = ConversationDetailController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ConversationDetailExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException.class)
    ProblemDetail notFound(ConversationNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
