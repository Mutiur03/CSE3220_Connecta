package com.connecta.backend.analytics.controller;

import com.connecta.backend.analytics.service.InvalidReportRangeException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Analytics failures as problem details: an unusable range or zone is a 400. */
@RestControllerAdvice(basePackageClasses = AnalyticsExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AnalyticsExceptionHandler {

    @ExceptionHandler(InvalidReportRangeException.class)
    ProblemDetail invalidRange(InvalidReportRangeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
