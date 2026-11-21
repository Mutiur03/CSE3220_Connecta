package com.connecta.backend.product.controller;

import com.connecta.backend.product.service.ProductCategoryNotFoundException;
import com.connecta.backend.product.service.ProductNameTakenException;
import com.connecta.backend.product.service.ProductNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before GlobalExceptionHandler, whose catch-all would otherwise answer these with a 500. */
@RestControllerAdvice(basePackageClasses = ProductController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProductExceptionHandler {

    @ExceptionHandler({ProductNotFoundException.class, ProductCategoryNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ProductNameTakenException.class)
    ProblemDetail nameTaken(ProductNameTakenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
