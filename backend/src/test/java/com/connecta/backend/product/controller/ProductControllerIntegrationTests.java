package com.connecta.backend.product.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.product.repository.ProductCategoryRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The product endpoints against real PostgreSQL (Flyway runs the migrations). Each endpoint has an allowed
 * role and a denied role, plus 401, the cross-tenant 404 and the duplicate-name 409. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ProductControllerIntegrationTests {

    private static final String AUTH = "Authorization";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductCategoryRepository categories;

    @Autowired
    JdbcTemplate jdbc;

    private Product stocked(Organization organization, String name, String sku, int stock, int reorderLevel) {
        Product product = products.saveAndFlush(new Product(organization, null, name, null, sku,
                new java.math.BigDecimal("2.50"), reorderLevel));
        jdbc.update("UPDATE products SET stock_quantity = ? WHERE id = ?", stock, product.getId());
        return product;
    }

    private static String productsPath(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/products";
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static final String VALID_BODY = "{\"name\":\"Shoes\",\"description\":\"Running\"}";

    private User newUser() {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(role(Roles.AGENT));
        return users.saveAndFlush(user);
    }

    private Role role(String name) {
        return roles.findByName(name).orElseThrow();
    }

    private Organization organization(String name) {
        return organizations.saveAndFlush(new Organization(name, "org-" + UUID.randomUUID()));
    }

    private void join(Organization organization, User user, String roleName) {
        memberships.saveAndFlush(new Membership(organization, user, role(roleName)));
    }

    private Product product(Organization organization, String name) {
        return products.saveAndFlush(new Product(organization, null, name, "Seeded"));
    }

    private ProductCategory category(Organization organization, String name) {
        return categories.saveAndFlush(new ProductCategory(organization, name, null));
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    @Nested
    class ListAndGet {

        @Test
        void aMemberSeesAPageOfProductsWithTotals() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            product(acme, "Hats");
            product(acme, "Shoes");

            mvc.perform(get(productsPath(acme)).param("size", "1").header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Hats"))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void aSizeAboveTheMaximumIsRejected() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(productsPath(acme)).param("size", "101").header(AUTH, bearer(agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(get(productsPath(acme))).andExpect(status().isUnauthorized());
        }

        @Test
        void aNonMemberGetsA404ForTheList() throws Exception {
            User outsider = newUser();
            Organization acme = organization("Acme");

            mvc.perform(get(productsPath(acme)).header(AUTH, bearer(outsider)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anotherTenantsProductIsA404() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, agent, Roles.AGENT);
            Product globexProduct = product(globex, "Secret");

            mvc.perform(get(productsPath(acme) + "/" + globexProduct.getId())
                            .header(AUTH, bearer(agent)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class CreateUpdateDelete {

        @Test
        void anAgentCannotCreateAProduct() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(json(post(productsPath(acme)), VALID_BODY).header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAdminCreatesAProductAndGetsItsLocation() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(json(post(productsPath(acme)), VALID_BODY).header(AUTH, bearer(admin)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(productsPath(acme))))
                    .andExpect(jsonPath("$.name").value("Shoes"))
                    .andExpect(jsonPath("$.categoryId").doesNotExist());
        }

        @Test
        void aBlankNameIsAValidationErrorOnTheField() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(json(post(productsPath(acme)), "{\"name\":\"  \"}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        @Test
        void aDuplicateNameIsAConflictWithoutStackTraceOrInternals() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            product(acme, "Shoes");

            MvcResult result = mvc.perform(json(post(productsPath(acme)), VALID_BODY)
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isConflict())
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            assertFalse(body.contains("Exception"));
            assertFalse(body.contains("at com."));
            assertFalse(body.toLowerCase().contains("sql"));
        }

        @Test
        void aCategoryOfAnotherOrganizationIsA404() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, admin, Roles.ADMIN);
            ProductCategory globexCategory = category(globex, "Footwear");

            mvc.perform(json(post(productsPath(acme)),
                            "{\"name\":\"Shoes\",\"categoryId\":\"" + globexCategory.getId() + "\"}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anAdminReplacesAProduct() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme, "Shoes");

            mvc.perform(json(put(productsPath(acme) + "/" + shoes.getId()),
                            "{\"name\":\"Running shoes\",\"description\":\"Light\"}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Running shoes"))
                    .andExpect(jsonPath("$.description").value("Light"));
        }

        @Test
        void anAgentCannotDeleteAProduct() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product shoes = product(acme, "Shoes");

            mvc.perform(delete(productsPath(acme) + "/" + shoes.getId()).header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAdminDeletesAProductAndItIsGoneAfterwards() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme, "Shoes");
            String path = productsPath(acme) + "/" + shoes.getId();

            mvc.perform(delete(path).header(AUTH, bearer(admin))).andExpect(status().isNoContent());
            mvc.perform(get(path).header(AUTH, bearer(admin))).andExpect(status().isNotFound());
        }
    }

    @Nested
    class InventoryFields {

        @Test
        void createReturnsSkuPriceStockAndStatus() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(json(post(productsPath(acme)),
                            "{\"name\":\"Shoes\",\"sku\":\"  SH-1 \",\"unitPrice\":19.99,\"reorderLevel\":5}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sku").value("SH-1"))
                    .andExpect(jsonPath("$.unitPrice").value(19.99))
                    .andExpect(jsonPath("$.stockQuantity").value(0))
                    .andExpect(jsonPath("$.reorderLevel").value(5))
                    .andExpect(jsonPath("$.stockStatus").value("OUT_OF_STOCK"));
        }

        @Test
        void missingPriceAndReorderLevelDefaultToZeroAndABlankSkuToNull() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(json(post(productsPath(acme)), "{\"name\":\"Shoes\",\"sku\":\"   \"}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sku").doesNotExist())
                    .andExpect(jsonPath("$.unitPrice").value(0))
                    .andExpect(jsonPath("$.reorderLevel").value(0));
        }

        @Test
        void aDuplicateSkuIgnoringCaseIsA409ButAnotherTenantMayReuseIt() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, admin, Roles.ADMIN);
            join(globex, admin, Roles.ADMIN);
            stocked(acme, "Existing", "SH-1", 0, 0);

            mvc.perform(json(post(productsPath(acme)), "{\"name\":\"Shoes\",\"sku\":\"sh-1\"}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isConflict());
            mvc.perform(json(post(productsPath(globex)), "{\"name\":\"Shoes\",\"sku\":\"sh-1\"}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isCreated());
        }

        @Test
        void updatingToAnotherProductsSkuIsA409AndKeepingYourOwnIsFine() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            stocked(acme, "Other", "TAKEN", 0, 0);
            Product mine = stocked(acme, "Mine", "MINE", 0, 0);
            String path = productsPath(acme) + "/" + mine.getId();

            mvc.perform(json(put(path), "{\"name\":\"Mine\",\"sku\":\"taken\"}").header(AUTH, bearer(admin)))
                    .andExpect(status().isConflict());
            mvc.perform(json(put(path), "{\"name\":\"Mine\",\"sku\":\"mine\",\"unitPrice\":3}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sku").value("mine"))
                    .andExpect(jsonPath("$.unitPrice").value(3));
        }

        @Test
        void invalidInventoryValuesAreValidationErrorsOnTheField() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            String longSku = "x".repeat(65);

            mvc.perform(json(post(productsPath(acme)), "{\"name\":\"A\",\"unitPrice\":-1}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.unitPrice").exists());
            mvc.perform(json(post(productsPath(acme)), "{\"name\":\"A\",\"unitPrice\":1.234}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.unitPrice").exists());
            mvc.perform(json(post(productsPath(acme)), "{\"name\":\"A\",\"reorderLevel\":-1}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.reorderLevel").exists());
            mvc.perform(json(post(productsPath(acme)), "{\"name\":\"A\",\"sku\":\"" + longSku + "\"}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.sku").exists());
        }

        @Test
        void anAgentCannotWriteInventoryFields() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product shoes = stocked(acme, "Shoes", null, 0, 0);

            mvc.perform(json(put(productsPath(acme) + "/" + shoes.getId()),
                            "{\"name\":\"Shoes\",\"unitPrice\":9}").header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aClientCannotSetTheStockQuantity() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);

            mvc.perform(json(post(productsPath(acme)), "{\"name\":\"Shoes\",\"stockQuantity\":99}")
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.stockQuantity").value(0));
        }

        @Test
        void getShowsTheComputedStatus() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product low = stocked(acme, "Low", null, 2, 5);

            mvc.perform(get(productsPath(acme) + "/" + low.getId()).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stockQuantity").value(2))
                    .andExpect(jsonPath("$.stockStatus").value("LOW_STOCK"));
        }
    }

    @Nested
    class ListFilters {

        @Test
        void qMatchesNameOrSkuIgnoringCase() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            stocked(acme, "Running shoe", "RS-1", 0, 0);
            stocked(acme, "Tote", "SHOE-BAG", 0, 0);
            stocked(acme, "Hat", null, 0, 0);

            mvc.perform(get(productsPath(acme)).param("q", "SHOE").header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].name").value("Running shoe"))
                    .andExpect(jsonPath("$.content[1].name").value("Tote"));
        }

        @Test
        void aPercentSignInQIsLiteral() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            stocked(acme, "100% cotton", null, 0, 0);
            stocked(acme, "Hat", null, 0, 0);

            mvc.perform(get(productsPath(acme)).param("q", "%").header(AUTH, bearer(agent)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("100% cotton"));
        }

        @Test
        void categoryIdKeepsOnlyThatCategory() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            ProductCategory shoes = category(acme, "Shoes");
            products.saveAndFlush(new Product(acme, shoes, "Runner", null));
            product(acme, "Loose item");

            mvc.perform(get(productsPath(acme)).param("categoryId", shoes.getId().toString())
                            .header(AUTH, bearer(agent)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Runner"));
        }

        @Test
        void anotherOrganizationsCategoryIsA404() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, agent, Roles.AGENT);
            ProductCategory theirs = category(globex, "Shoes");

            mvc.perform(get(productsPath(acme)).param("categoryId", theirs.getId().toString())
                            .header(AUTH, bearer(agent)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void stockStatusKeepsOnlyThatStatus() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            stocked(acme, "A out", null, 0, 5);
            stocked(acme, "B low", null, 3, 5);
            stocked(acme, "C in", null, 30, 5);
            stocked(acme, "D in no level", null, 1, 0);

            mvc.perform(get(productsPath(acme)).param("stockStatus", "OUT_OF_STOCK").header(AUTH, bearer(agent)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("A out"));
            mvc.perform(get(productsPath(acme)).param("stockStatus", "LOW_STOCK").header(AUTH, bearer(agent)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("B low"));
            mvc.perform(get(productsPath(acme)).param("stockStatus", "IN_STOCK").header(AUTH, bearer(agent)))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].name").value("C in"))
                    .andExpect(jsonPath("$.content[1].name").value("D in no level"));
        }

        @Test
        void anUnknownStockStatusIsA400() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);

            mvc.perform(get(productsPath(acme)).param("stockStatus", "PLENTY").header(AUTH, bearer(agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void filtersCombineAndNeverShowAnotherTenantsProducts() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, agent, Roles.AGENT);
            stocked(acme, "Low shoe", "S-1", 1, 5);
            stocked(acme, "Fine shoe", "S-2", 50, 5);
            stocked(globex, "Their low shoe", "S-3", 1, 5);

            mvc.perform(get(productsPath(acme)).param("q", "shoe").param("stockStatus", "LOW_STOCK")
                            .header(AUTH, bearer(agent)))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Low shoe"));
        }

        @Test
        void filteredListsStayPaged() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            stocked(acme, "Shoe A", null, 0, 0);
            stocked(acme, "Shoe B", null, 0, 0);

            mvc.perform(get(productsPath(acme)).param("q", "shoe").param("size", "1").param("page", "1")
                            .header(AUTH, bearer(agent)))
                    .andExpect(jsonPath("$.content[0].name").value("Shoe B"))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void anAnonymousCallerIsUnauthorizedAndANonMemberGetsA404() throws Exception {
            User outsider = newUser();
            Organization acme = organization("Acme");

            mvc.perform(get(productsPath(acme)).param("q", "x")).andExpect(status().isUnauthorized());
            mvc.perform(get(productsPath(acme)).param("q", "x").header(AUTH, bearer(outsider)))
                    .andExpect(status().isNotFound());
        }
    }
}
