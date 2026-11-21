package com.connecta.backend.product.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.connecta.backend.product.service.ProductCategoryNameTakenException;
import com.connecta.backend.product.service.ProductCategoryNotFoundException;
import com.connecta.backend.product.service.ProductSkuTakenException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** The bodies carry a status and a message only: no stack trace, no SQL, no class names. */
class ProductExceptionHandlerTests {

    private final ProductExceptionHandler handler = new ProductExceptionHandler();

    private static void assertCleanBody(ProblemDetail problem) {
        String text = problem.getDetail() + " " + problem.getTitle();
        assertFalse(text.contains("Exception"), "class name leaked: " + text);
        assertFalse(text.toLowerCase().contains("select"), "SQL leaked: " + text);
        assertFalse(problem.getProperties() != null && problem.getProperties().containsKey("stackTrace"));
    }

    @Nested
    class CategoryErrors {

        @Test
        void notFoundIsAProblemWithoutInternals() {
            ProblemDetail problem = handler.notFound(new ProductCategoryNotFoundException());

            assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
            assertEquals("Category not found", problem.getDetail());
            assertCleanBody(problem);
        }

        @Test
        void nameTakenIsAConflictWithoutInternals() {
            ProblemDetail problem = handler.nameTaken(new ProductCategoryNameTakenException());

            assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
            assertCleanBody(problem);
        }
    }

    @Nested
    class SkuErrors {

        @Test
        void skuTakenIsAConflictWithoutInternals() {
            ProblemDetail problem = handler.nameTaken(new ProductSkuTakenException());

            assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
            assertEquals("A product with this SKU already exists", problem.getDetail());
            assertCleanBody(problem);
        }
    }
}
