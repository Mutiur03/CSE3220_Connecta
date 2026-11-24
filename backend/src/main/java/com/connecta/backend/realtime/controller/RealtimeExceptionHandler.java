package com.connecta.backend.realtime.controller;

import com.connecta.backend.realtime.service.TooManyTicketsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before GlobalExceptionHandler, whose catch-all would otherwise answer this with a 500. */
@RestControllerAdvice(basePackageClasses = RealtimeTicketController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class RealtimeExceptionHandler {

    @ExceptionHandler(TooManyTicketsException.class)
    ProblemDetail tooManyTickets(TooManyTicketsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }
}
