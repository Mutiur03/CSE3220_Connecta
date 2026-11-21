package com.connecta.backend.product.controller;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.product.domain.InsufficientStockException;
import com.connecta.backend.product.domain.InvalidStockQuantityException;
import com.connecta.backend.product.service.ProductCategoryNameTakenException;
import com.connecta.backend.product.service.ProductCategoryNotFoundException;
import com.connecta.backend.product.service.ProductNameTakenException;
import com.connecta.backend.product.service.ProductNotFoundException;
import com.connecta.backend.product.service.ProductSkuTakenException;
import com.connecta.backend.product.service.StockMovementNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Product and category failures as problem details. Runs before GlobalExceptionHandler, whose catch-all would give a 500. */
@RestControllerAdvice(basePackageClasses = ProductController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ProductExceptionHandler {

    @ExceptionHandler({ProductNotFoundException.class, ProductCategoryNotFoundException.class,
            StockMovementNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidStockQuantityException.class)
    ProblemDetail invalidQuantity(InvalidStockQuantityException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail insufficientStock(InsufficientStockException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler({ProductNameTakenException.class, ProductSkuTakenException.class,
            ProductCategoryNameTakenException.class})
    ProblemDetail nameTaken(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AccountNotAvailableException.class)
    ProblemDetail accountNotAvailable(AccountNotAvailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
}
