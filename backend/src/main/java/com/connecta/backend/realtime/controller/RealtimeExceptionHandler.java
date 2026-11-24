package com.connecta.backend.realtime.controller;

import com.connecta.backend.config.RealtimeProperties;
import com.connecta.backend.realtime.service.TooManyTicketsException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before GlobalExceptionHandler, whose catch-all would otherwise answer this with a 500. */
@RestControllerAdvice(basePackageClasses = RealtimeTicketController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class RealtimeExceptionHandler {

    private final RealtimeProperties properties;

    @ExceptionHandler(TooManyTicketsException.class)
    ResponseEntity<ProblemDetail> tooManyTickets(TooManyTicketsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(properties.ticketRateWindow().toSeconds()))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage()));
    }
}
