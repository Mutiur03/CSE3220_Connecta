package com.connecta.backend.share.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.inbox.dto.SendMessageRequest;
import com.connecta.backend.product.domain.StockStatus;
import com.connecta.backend.product.dto.ProductResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductMessageComposerTests {

    private final ProductMessageComposer composer = new ProductMessageComposer();

    private static ProductResponse product(String name, String description, String sku, String price,
            StockStatus status) {
        return new ProductResponse(UUID.randomUUID(), name, description, null, null, sku, new BigDecimal(price), 5,
                0, status, null, null);
    }

    @Test
    void composesEveryField() {
        String text = composer.compose(product("Desk Lamp", "Warm light.", "LAMP-1", "19.90", StockStatus.IN_STOCK));

        assertEquals("Desk Lamp\nPrice: 19.90\nSKU: LAMP-1\nIn stock\n\nWarm light.", text);
    }

    @Test
    void omitsTheDescriptionWhenNullOrBlank() {
        String expected = "Desk Lamp\nPrice: 19.90\nSKU: LAMP-1\nIn stock";

        assertEquals(expected, composer.compose(product("Desk Lamp", null, "LAMP-1", "19.90", StockStatus.IN_STOCK)));
        assertEquals(expected, composer.compose(product("Desk Lamp", "  ", "LAMP-1", "19.90", StockStatus.IN_STOCK)));
    }

    @Test
    void omitsTheSkuLineWhenThereIsNone() {
        String text = composer.compose(product("Desk Lamp", null, null, "19.90", StockStatus.IN_STOCK));

        assertFalse(text.contains("SKU"));
        assertEquals("Desk Lamp\nPrice: 19.90\nIn stock", text);
    }

    @Test
    void saysWhenAProductIsOutOfStock() {
        assertTrue(composer.compose(product("Desk Lamp", null, null, "0.00", StockStatus.OUT_OF_STOCK))
                .endsWith("Out of stock"));
    }

    @Test
    void saysWhenAProductIsLowOnStock() {
        assertTrue(composer.compose(product("Desk Lamp", null, null, "1.00", StockStatus.LOW_STOCK))
                .endsWith("Low stock"));
    }

    @Test
    void cutsALongDescriptionWithAnEllipsisToTheLimit() {
        String text = composer.compose(product("Desk Lamp", "word ".repeat(500), "LAMP-1", "19.90",
                StockStatus.IN_STOCK));

        assertEquals(SendMessageRequest.TEXT_MAX_LENGTH, text.length());
        assertTrue(text.endsWith(ProductMessageComposer.ELLIPSIS));
        assertTrue(text.startsWith("Desk Lamp\nPrice: 19.90\nSKU: LAMP-1\nIn stock\n\n"));
    }

    @Test
    void keepsADescriptionThatExactlyFits() {
        String facts = "Desk Lamp\nPrice: 19.90\nIn stock\n\n";
        String description = "x".repeat(SendMessageRequest.TEXT_MAX_LENGTH - facts.length());

        String text = composer.compose(product("Desk Lamp", description, null, "19.90", StockStatus.IN_STOCK));

        assertEquals(facts + description, text);
    }
}
