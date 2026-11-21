package com.connecta.backend.product.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StockStatusTests {

    @Nested
    class Rule {

        @ParameterizedTest
        @CsvSource({
                "0, 0, OUT_OF_STOCK",
                "0, 10, OUT_OF_STOCK",
                "1, 0, IN_STOCK",
                "50, 0, IN_STOCK",
                "1, 10, LOW_STOCK",
                "10, 10, LOW_STOCK",
                "11, 10, IN_STOCK"})
        void followsTheContract(int stock, int reorderLevel, StockStatus expected) {
            assertEquals(expected, StockStatus.of(stock, reorderLevel));
        }

        @Test
        void aZeroReorderLevelNeverMeansLowStock() {
            assertEquals(StockStatus.IN_STOCK, StockStatus.of(1, 0));
        }
    }
}
