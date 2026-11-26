package com.connecta.backend.inbox.controller;

import com.connecta.backend.inbox.service.AssigneeNotAvailableException;
import com.connecta.backend.inbox.service.AssignmentUnchangedException;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.org.service.MemberNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Assignment failures as problem details: 404 for unknown or foreign ids, 422 for a rule that blocks it. */
@RestControllerAdvice(basePackageClasses = ConversationAssignmentController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ConversationAssignmentExceptionHandler {

    @ExceptionHandler({ConversationNotFoundException.class, MemberNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({AssignmentUnchangedException.class, AssigneeNotAvailableException.class})
    ProblemDetail refused(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }
}
