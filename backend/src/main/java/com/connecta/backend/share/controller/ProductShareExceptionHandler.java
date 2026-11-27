package com.connecta.backend.share.controller;

import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.meta.service.ChannelNotConnectedException;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.meta.service.ConversationNotLinkedException;
import com.connecta.backend.meta.service.MessagingWindowClosedException;
import com.connecta.backend.product.service.ProductNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Product sharing failures as problem details. Runs before GlobalExceptionHandler, whose catch-all would give a 500. */
@RestControllerAdvice(basePackageClasses = ProductShareSearchController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProductShareExceptionHandler {

    @ExceptionHandler({ConversationNotFoundException.class, ProductNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({ChannelNotConnectedException.class, ConversationNotLinkedException.class,
            MessagingWindowClosedException.class})
    ProblemDetail sendRefused(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ChannelSendFailedException.class)
    ProblemDetail sendFailed(ChannelSendFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Meta could not deliver the message. Try again.");
    }
}
