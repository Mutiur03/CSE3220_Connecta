package com.connecta.backend.product.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.product.repository.StockMovementRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Stock movement endpoints against real PostgreSQL. Any member may record and read; a non-member and another
 * tenant's product or movement are 404; an ISSUE beyond stock is 422. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class StockMovementControllerIntegrationTests {

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
    StockMovementRepository movements;

    @Autowired
    JdbcTemplate jdbc;

    private static final String RECEIVE_BODY = "{\"type\":\"RECEIVE\",\"quantity\":5,\"reason\":\"Delivery\"}";

    private static String productsPath(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/products";
    }

    private static String movementsPath(Organization organization, Product product) {
        return productsPath(organization) + "/" + product.getId() + "/stock-movements";
    }

    private static String orgMovementsPath(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/stock-movements";
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

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

    private Product product(Organization organization, String name, int stock) {
        Product product = products.saveAndFlush(new Product(organization, null, name, null, null,
                new BigDecimal("2.50"), 0));
        jdbc.update("UPDATE products SET stock_quantity = ? WHERE id = ?", stock, product.getId());
        return product;
    }

    private void createdAt(String movementId, Instant when) {
        jdbc.update("UPDATE stock_movements SET created_at = ? WHERE id = ?", Timestamp.from(when),
                UUID.fromString(movementId));
    }

    private int stockOf(Product product) {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class,
                product.getId());
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    @Nested
    class Record {

        @Test
        void aMemberRecordsAReceiveAndGetsTheMovementWithALocation() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product hats = product(acme, "Hats", 2);

            mvc.perform(json(post(movementsPath(acme, hats)).header(AUTH, bearer(agent)), RECEIVE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString(
                            "/api/organizations/" + acme.getId() + "/stock-movements/")))
                    .andExpect(jsonPath("$.type").value("RECEIVE"))
                    .andExpect(jsonPath("$.productName").value("Hats"))
                    .andExpect(jsonPath("$.quantityDelta").value(5))
                    .andExpect(jsonPath("$.quantityAfter").value(7))
                    .andExpect(jsonPath("$.reason").value("Delivery"))
                    .andExpect(jsonPath("$.createdBy.fullName").value("Test User"))
                    .andExpect(jsonPath("$.createdBy.id").value(agent.getId().toString()));
            assertEquals(7, stockOf(hats));
        }

        @Test
        void anIssueBeyondTheStockIsUnprocessableAndChangesNothing() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product hats = product(acme, "Hats", 2);

            mvc.perform(json(post(movementsPath(acme, hats)).header(AUTH, bearer(agent)),
                            "{\"type\":\"ISSUE\",\"quantity\":3}"))
                    .andExpect(status().is(422))
                    .andExpect(jsonPath("$.status").value(422));
            assertEquals(2, stockOf(hats));
        }

        @Test
        void aQuantityBelowTheTypeMinimumIsBadRequest() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product hats = product(acme, "Hats", 2);

            mvc.perform(json(post(movementsPath(acme, hats)).header(AUTH, bearer(agent)),
                            "{\"type\":\"RECEIVE\",\"quantity\":0}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aMalformedMovementIsBadRequestWithFieldErrors() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product hats = product(acme, "Hats", 2);

            mvc.perform(json(post(movementsPath(acme, hats)).header(AUTH, bearer(agent)),
                            "{\"quantity\":-1,\"reason\":\"" + "x".repeat(256) + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").exists())
                    .andExpect(jsonPath("$.errors.quantity").exists())
                    .andExpect(jsonPath("$.errors.reason").exists());
        }

        @Test
        void aNonNumericQuantityIsBadRequest() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product hats = product(acme, "Hats", 2);

            mvc.perform(json(post(movementsPath(acme, hats)).header(AUTH, bearer(agent)),
                            "{\"type\":\"RECEIVE\",\"quantity\":\"many\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");
            Product hats = product(acme, "Hats", 2);

            mvc.perform(json(post(movementsPath(acme, hats)), RECEIVE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void aNonMemberGetsANotFound() throws Exception {
            User outsider = newUser();
            Organization acme = organization("Acme");
            Product hats = product(acme, "Hats", 2);

            mvc.perform(json(post(movementsPath(acme, hats)).header(AUTH, bearer(outsider)), RECEIVE_BODY))
                    .andExpect(status().isNotFound());
            assertEquals(2, stockOf(hats));
        }

        @Test
        void anotherTenantsProductIsNotFoundAndItsStockIsUntouched() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, agent, Roles.AGENT);
            Product secret = product(globex, "Secret", 2);

            mvc.perform(json(post(movementsPath(acme, secret)).header(AUTH, bearer(agent)), RECEIVE_BODY))
                    .andExpect(status().isNotFound());
            assertEquals(2, stockOf(secret));
        }
    }

    @Nested
    class Read {

        @Test
        void aProductsHistoryIsPagedNewestFirst() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product hats = product(acme, "Hats", 0);
            String older = recordAndGetId(agent, acme, hats, "{\"type\":\"RECEIVE\",\"quantity\":1}");
            String newer = recordAndGetId(agent, acme, hats, "{\"type\":\"RECEIVE\",\"quantity\":2}");
            createdAt(older, Instant.parse("2026-01-01T10:00:00Z"));
            createdAt(newer, Instant.parse("2026-01-02T10:00:00Z"));

            mvc.perform(get(movementsPath(acme, hats)).param("size", "1").header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(newer))
                    .andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void theOrganizationHistoryCanBeNarrowedToOneProduct() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product hats = product(acme, "Hats", 0);
            Product shoes = product(acme, "Shoes", 0);
            recordAndGetId(agent, acme, hats, "{\"type\":\"RECEIVE\",\"quantity\":1}");
            recordAndGetId(agent, acme, shoes, "{\"type\":\"RECEIVE\",\"quantity\":1}");

            mvc.perform(get(orgMovementsPath(acme)).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
            mvc.perform(get(orgMovementsPath(acme)).param("productId", hats.getId().toString())
                            .header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].productName").value("Hats"));
        }

        @Test
        void anotherTenantsProductIdInTheOrganizationHistoryIsNotFound() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, agent, Roles.AGENT);
            Product secret = product(globex, "Secret", 0);

            mvc.perform(get(orgMovementsPath(acme)).param("productId", secret.getId().toString())
                            .header(AUTH, bearer(agent)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void aSizeAboveTheMaximumIsBadRequest() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product hats = product(acme, "Hats", 0);

            mvc.perform(get(movementsPath(acme, hats)).param("size", "101").header(AUTH, bearer(agent)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aMovementOfAnotherTenantIsNotFound() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, agent, Roles.AGENT);
            Product secret = product(globex, "Secret", 0);
            String movementId = recordAndGetId(newUserIn(globex), globex, secret,
                    "{\"type\":\"RECEIVE\",\"quantity\":1}");

            mvc.perform(get(orgMovementsPath(acme) + "/" + movementId).header(AUTH, bearer(agent)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void aNonMemberGetsANotFoundForTheHistory() throws Exception {
            User outsider = newUser();
            Organization acme = organization("Acme");
            Product hats = product(acme, "Hats", 0);

            mvc.perform(get(movementsPath(acme, hats)).header(AUTH, bearer(outsider)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(get(orgMovementsPath(acme))).andExpect(status().isUnauthorized());
        }
    }

    /** A member of the organization records one movement through the API and returns the new movement's id. */
    private String recordAndGetId(User user, Organization organization, Product product, String body)
            throws Exception {
        String response = mvc.perform(json(post(movementsPath(organization, product)).header(AUTH, bearer(user)), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private User newUserIn(Organization organization) {
        User user = newUser();
        join(organization, user, Roles.AGENT);
        return user;
    }
}
