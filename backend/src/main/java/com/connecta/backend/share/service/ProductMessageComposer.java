package com.connecta.backend.share.service;

import com.connecta.backend.inbox.dto.SendMessageRequest;
import com.connecta.backend.product.domain.StockStatus;
import com.connecta.backend.product.dto.ProductResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the customer-facing text for a product: name, price, SKU, availability, then the description. The result
 * never exceeds {@link SendMessageRequest#TEXT_MAX_LENGTH}; the description is the part that gets cut, with an
 * ellipsis. Pure: no state, no I/O.
 */
@Component
public class ProductMessageComposer {

    static final String ELLIPSIS = "…";

    private static final String PRICE_PREFIX = "Price: ";
    private static final String SKU_PREFIX = "SKU: ";
    private static final String LINE_BREAK = "\n";
    private static final String BLANK_LINE = "\n\n";

    private static final Map<StockStatus, String> AVAILABILITY = Map.of(
            StockStatus.IN_STOCK, "In stock",
            StockStatus.LOW_STOCK, "Low stock",
            StockStatus.OUT_OF_STOCK, "Out of stock");

    public String compose(ProductResponse product) {
        String facts = String.join(LINE_BREAK, facts(product));
        String description = product.description() == null ? "" : product.description().strip();
        String text = description.isEmpty() ? facts : facts + BLANK_LINE + description;
        return fit(text);
    }

    private List<String> facts(ProductResponse product) {
        List<String> lines = new ArrayList<>();
        lines.add(product.name());
        lines.add(PRICE_PREFIX + product.unitPrice().toPlainString());
        if (product.sku() != null && !product.sku().isBlank()) {
            lines.add(SKU_PREFIX + product.sku());
        }
        lines.add(AVAILABILITY.get(product.stockStatus()));
        return lines;
    }

    private String fit(String text) {
        int max = SendMessageRequest.TEXT_MAX_LENGTH;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - ELLIPSIS.length()).stripTrailing() + ELLIPSIS;
    }
}
