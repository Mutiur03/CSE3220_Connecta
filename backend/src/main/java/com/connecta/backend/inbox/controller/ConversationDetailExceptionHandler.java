package com.connecta.backend.inbox.controller;

import com.connecta.backend.file.service.PublicBaseUrlMissingException;
import com.connecta.backend.inbox.service.AttachmentTooLargeException;
import com.connecta.backend.inbox.service.ConversationNotFoundException;
import com.connecta.backend.inbox.service.EmptyAttachmentException;
import com.connecta.backend.inbox.service.UnsupportedAttachmentTypeException;
import com.connecta.backend.meta.service.ChannelNotConnectedException;
import com.connecta.backend.meta.service.ConversationNotLinkedException;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.meta.service.MessagingWindowClosedException;
import com.connecta.backend.meta.service.UnsupportedAttachmentException;
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

    @ExceptionHandler({ChannelNotConnectedException.class, ConversationNotLinkedException.class,
            MessagingWindowClosedException.class})
    ProblemDetail sendRefused(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler({UnsupportedAttachmentException.class, PublicBaseUrlMissingException.class})
    ProblemDetail attachmentRefused(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(EmptyAttachmentException.class)
    ProblemDetail emptyAttachment(EmptyAttachmentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AttachmentTooLargeException.class)
    ProblemDetail attachmentTooLarge(AttachmentTooLargeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
    }

    @ExceptionHandler(UnsupportedAttachmentTypeException.class)
    ProblemDetail unsupportedAttachmentType(UnsupportedAttachmentTypeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler(ChannelSendFailedException.class)
    ProblemDetail sendFailed(ChannelSendFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Meta could not deliver the message. Try again.");
    }
}
