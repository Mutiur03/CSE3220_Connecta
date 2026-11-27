package com.connecta.backend.product.repository;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.product.domain.StockStatus;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;

/**
 * Real PostgreSQL: the product table, its optional category link and the same-organization
 * foreign key are enforced by the database. Skipped when Docker is absent.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ProductRepositoryIntegrationTests extends SharedPostgres {

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    ProductCategoryRepository categories;

    @Autowired
    ProductRepository products;

    @Autowired
    JdbcTemplate jdbc;

    /** Stock has no public mutator yet (stock movements come later), so tests set the column directly. */
    private Product withStock(Product product, int stock) {
        jdbc.update("UPDATE products SET stock_quantity = ? WHERE id = ?", stock, product.getId());
        return product;
    }

    private Product save(Organization org, String name, String sku, int stock, int reorderLevel) {
        Product product = products.saveAndFlush(
                new Product(org, null, name, null, sku, BigDecimal.TEN, reorderLevel));
        return withStock(product, stock);
    }

    private List<String> names(Organization org, org.springframework.data.jpa.domain.Specification<Product> filter) {
        return products.findAll(ProductSpecifications.inOrganization(org.getId()).and(filter),
                        PageRequest.of(0, 50, Sort.by("name")))
                .map(Product::getName).getContent();
    }

    private Organization newOrganization() {
        return organizations.saveAndFlush(new Organization("Org", "org-" + System.nanoTime()));
    }

    @Nested
    class CategoryLink {

        @Test
        void aProductCanBeUncategorized() {
            Organization org = newOrganization();
            Product product = products.saveAndFlush(new Product(org, null, "Mystery item", null));

            Product found = products.findByIdAndOrganizationId(product.getId(), org.getId()).orElseThrow();

            assertNull(found.getCategory());
        }

        @Test
        void listsOnlyTheProductsInTheRequestedCategory() {
            Organization org = newOrganization();
            ProductCategory shoes = categories.saveAndFlush(new ProductCategory(org, "Shoes", null));
            ProductCategory bags = categories.saveAndFlush(new ProductCategory(org, "Bags", null));
            products.saveAndFlush(new Product(org, shoes, "Running shoe", null));
            products.saveAndFlush(new Product(org, bags, "Tote", null));

            var page = products.findAll(ProductSpecifications.inOrganization(org.getId())
                    .and(ProductSpecifications.inCategory(shoes.getId())), PageRequest.of(0, 20, Sort.by("name")));

            assertEquals(1, page.getTotalElements());
            assertEquals("Running shoe", page.getContent().get(0).getName());
        }

        @Test
        void aProductCannotPointAtAnotherOrganizationsCategory() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            ProductCategory foreignShoes = categories.saveAndFlush(new ProductCategory(other, "Shoes", null));

            assertThrows(DataIntegrityViolationException.class,
                    () -> products.saveAndFlush(new Product(mine, foreignShoes, "Running shoe", null)));
        }
    }

    @Nested
    class TenantScope {

        @Test
        void lookupByIdReturnsNothingForAnotherOrganization() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            Product product = products.saveAndFlush(new Product(mine, null, "Running shoe", null));

            assertTrue(products.findByIdAndOrganizationId(product.getId(), mine.getId()).isPresent());
            assertFalse(products.findByIdAndOrganizationId(product.getId(), other.getId()).isPresent());
        }

        @Test
        void listOnlyReturnsTheOrganizationsOwnProducts() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            products.saveAndFlush(new Product(mine, null, "Running shoe", null));
            products.saveAndFlush(new Product(other, null, "Tote", null));

            var page = products.findAll(ProductSpecifications.inOrganization(mine.getId()),
                    PageRequest.of(0, 20, Sort.by("name")));

            assertEquals(1, page.getTotalElements());
            assertEquals("Running shoe", page.getContent().get(0).getName());
        }

        @Test
        void categoryFilterDoesNotLeakAnotherOrganizationsProducts() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            ProductCategory otherShoes = categories.saveAndFlush(new ProductCategory(other, "Shoes", null));
            products.saveAndFlush(new Product(other, otherShoes, "Their shoe", null));

            var page = products.findAll(ProductSpecifications.inOrganization(mine.getId())
                    .and(ProductSpecifications.inCategory(otherShoes.getId())), PageRequest.of(0, 20, Sort.by("name")));

            assertEquals(0, page.getTotalElements());
        }
    }

    @Nested
    class Constraints {

        @Test
        void nameIsUniqueWithinOneOrganizationOnly() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            products.saveAndFlush(new Product(mine, null, "Running shoe", null));

            assertThrows(DataIntegrityViolationException.class,
                    () -> products.saveAndFlush(new Product(mine, null, "Running shoe", null)));
            products.saveAndFlush(new Product(other, null, "Running shoe", null));
        }

        @Test
        void existsByNameIsScopedToTheOrganization() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            products.saveAndFlush(new Product(mine, null, "Running shoe", null));

            assertTrue(products.existsByOrganizationIdAndName(mine.getId(), "Running shoe"));
            assertFalse(products.existsByOrganizationIdAndName(other.getId(), "Running shoe"));
        }
    }

    @Nested
    class InventoryColumns {

        @Test
        void aSkuIsUniqueInOneOrganizationIgnoringCaseButNotAcrossOrganizations() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            save(mine, "Shoe", "SH-1", 0, 0);

            assertThrows(DataIntegrityViolationException.class, () -> save(mine, "Boot", "sh-1", 0, 0));
            save(other, "Shoe", "SH-1", 0, 0);
        }

        @Test
        void manyProductsWithoutASkuAreAllowed() {
            Organization org = newOrganization();
            save(org, "A", null, 0, 0);
            save(org, "B", null, 0, 0);

            assertEquals(2, products.count(ProductSpecifications.inOrganization(org.getId())));
        }

        @Test
        void existsBySkuIgnoresCaseAndIsScopedToTheOrganization() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            save(mine, "Shoe", "SH-1", 0, 0);

            assertTrue(products.existsByOrganizationIdAndSkuIgnoreCase(mine.getId(), "sh-1"));
            assertFalse(products.existsByOrganizationIdAndSkuIgnoreCase(other.getId(), "SH-1"));
        }

        @Test
        void negativePriceStockOrReorderLevelIsRejectedByTheDatabase() {
            Organization org = newOrganization();
            assertThrows(DataIntegrityViolationException.class, () -> products.saveAndFlush(
                    new Product(org, null, "A", null, null, new BigDecimal("-1"), 0)));
            assertThrows(DataIntegrityViolationException.class, () -> products.saveAndFlush(
                    new Product(org, null, "B", null, null, BigDecimal.ZERO, -1)));
            Product ok = save(org, "C", null, 0, 0);
            assertThrows(DataIntegrityViolationException.class, () -> withStock(ok, -1));
        }

        @Test
        void priceAndReorderLevelRoundTrip() {
            Organization org = newOrganization();
            Product saved = products.saveAndFlush(
                    new Product(org, null, "Shoe", null, "SH-1", new BigDecimal("19.99"), 3));

            Product found = products.findByIdAndOrganizationId(saved.getId(), org.getId()).orElseThrow();

            assertEquals("SH-1", found.getSku());
            assertEquals(new BigDecimal("19.99"), found.getUnitPrice());
            assertEquals(3, found.getReorderLevel());
        }
    }

    @Nested
    class Filters {

        @Test
        void textMatchesNameOrSkuIgnoringCase() {
            Organization org = newOrganization();
            save(org, "Running shoe", "RS-100", 0, 0);
            save(org, "Tote", "SHOE-BAG", 0, 0);
            save(org, "Hat", null, 0, 0);

            assertEquals(List.of("Running shoe", "Tote"), names(org, ProductSpecifications.nameOrSkuContains("SHOE")));
            assertEquals(List.of("Running shoe"), names(org, ProductSpecifications.nameOrSkuContains("rs-1")));
        }

        @Test
        void likeWildcardsInTheTextMatchLiterally() {
            Organization org = newOrganization();
            save(org, "100% cotton", null, 0, 0);
            save(org, "Plain", "A_B", 0, 0);
            save(org, "AxB", null, 0, 0);
            save(org, "Back\\slash", null, 0, 0);

            assertEquals(List.of("100% cotton"), names(org, ProductSpecifications.nameOrSkuContains("%")));
            assertEquals(List.of("Plain"), names(org, ProductSpecifications.nameOrSkuContains("a_b")));
            assertEquals(List.of("Back\\slash"), names(org, ProductSpecifications.nameOrSkuContains("\\")));
        }

        @Test
        void stockStatusFiltersAgreeWithTheStatusRuleForEveryCase() {
            Organization org = newOrganization();
            int[][] cases = {{0, 0}, {0, 5}, {1, 0}, {9, 0}, {1, 5}, {5, 5}, {6, 5}};
            for (int i = 0; i < cases.length; i++) {
                save(org, "P" + i, null, cases[i][0], cases[i][1]);
            }

            for (StockStatus status : StockStatus.values()) {
                List<String> expected = new java.util.ArrayList<>();
                for (int i = 0; i < cases.length; i++) {
                    if (StockStatus.of(cases[i][0], cases[i][1]) == status) {
                        expected.add("P" + i);
                    }
                }
                assertEquals(expected, names(org, ProductSpecifications.withStockStatus(status)), status.name());
            }
        }

        @Test
        void filtersCombineWithAnd() {
            Organization org = newOrganization();
            ProductCategory shoes = categories.saveAndFlush(new ProductCategory(org, "Shoes", null));
            Product low = products.saveAndFlush(new Product(org, shoes, "Low shoe", null, null, BigDecimal.ONE, 5));
            withStock(low, 2);
            Product fine = products.saveAndFlush(new Product(org, shoes, "Fine shoe", null, null, BigDecimal.ONE, 5));
            withStock(fine, 50);
            products.saveAndFlush(new Product(org, null, "Low hat", null, null, BigDecimal.ONE, 5));

            var spec = ProductSpecifications.inCategory(shoes.getId())
                    .and(ProductSpecifications.withStockStatus(StockStatus.LOW_STOCK))
                    .and(ProductSpecifications.nameOrSkuContains("shoe"));

            assertEquals(List.of("Low shoe"), names(org, spec));
        }

        @Test
        void filtersNeverLeakAnotherOrganizationsProducts() {
            Organization mine = newOrganization();
            Organization other = newOrganization();
            save(other, "Their shoe", "SH-1", 0, 0);

            assertEquals(List.of(), names(mine, ProductSpecifications.nameOrSkuContains("shoe")));
            assertEquals(List.of(), names(mine, ProductSpecifications.withStockStatus(StockStatus.OUT_OF_STOCK)));
        }

        @Test
        void aPageIsStableAndTheCategoryIsFetchedWithoutExtraQueries() {
            Organization org = newOrganization();
            ProductCategory shoes = categories.saveAndFlush(new ProductCategory(org, "Shoes", null));
            for (int i = 0; i < 3; i++) {
                products.saveAndFlush(new Product(org, shoes, "P" + i, null, null, BigDecimal.ONE, 0));
            }

            var page = products.findAll(ProductSpecifications.inOrganization(org.getId()),
                    PageRequest.of(0, 2, Sort.by("name").ascending().and(Sort.by("id"))));

            assertEquals(3, page.getTotalElements());
            assertEquals(2, page.getContent().size());
            assertTrue(Hibernate.isInitialized(page.getContent().get(0).getCategory()));
        }
    }
}
