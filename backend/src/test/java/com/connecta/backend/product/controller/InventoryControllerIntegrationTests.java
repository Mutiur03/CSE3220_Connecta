package com.connecta.backend.product.controller;

import com.connecta.backend.support.SharedPostgres;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** The inventory summary against real PostgreSQL: totals, stock value and the low and out counts. Skipped without Docker. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class InventoryControllerIntegrationTests extends SharedPostgres {

    private static final String AUTH = "Authorization";

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
    JdbcTemplate jdbc;

    private static String summaryPath(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/inventory/summary";
    }

    private User newUser() {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(roles.findByName(Roles.AGENT).orElseThrow());
        return users.saveAndFlush(user);
    }

    private Organization organization(String name) {
        return organizations.saveAndFlush(new Organization(name, "org-" + UUID.randomUUID()));
    }

    private void join(Organization organization, User user) {
        Role agent = roles.findByName(Roles.AGENT).orElseThrow();
        memberships.saveAndFlush(new Membership(organization, user, agent));
    }

    /** A product with a price, a reorder level and a stock level (set directly, as movements are tested apart). */
    private void product(Organization organization, String name, String price, int reorderLevel, int stock) {
        Product product = products.saveAndFlush(new Product(organization, null, name, null, null,
                new BigDecimal(price), reorderLevel));
        jdbc.update("UPDATE products SET stock_quantity = ? WHERE id = ?", stock, product.getId());
    }

    @Nested
    class Summary {

        @Test
        void aMemberSeesTotalsStockValueAndLowAndOutCounts() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent);
            product(acme, "Hats", "2.50", 0, 4);     // in stock: no reorder level set
            product(acme, "Shoes", "2.50", 5, 3);    // low stock
            product(acme, "Bags", "10.00", 2, 0);    // out of stock

            mvc.perform(get(summaryPath(acme)).header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productCount").value(3))
                    .andExpect(jsonPath("$.totalUnits").value(7))
                    .andExpect(jsonPath("$.stockValue").value(17.50))
                    .andExpect(jsonPath("$.lowStockCount").value(1))
                    .andExpect(jsonPath("$.outOfStockCount").value(1));
        }

        @Test
        void anEmptyOrganizationReadsAsZeros() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent);

            mvc.perform(get(summaryPath(acme)).header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productCount").value(0))
                    .andExpect(jsonPath("$.totalUnits").value(0))
                    .andExpect(jsonPath("$.stockValue").value(0.00))
                    .andExpect(jsonPath("$.lowStockCount").value(0))
                    .andExpect(jsonPath("$.outOfStockCount").value(0));
        }

        @Test
        void anotherTenantsProductsAreNotCounted() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, agent);
            product(acme, "Hats", "1.00", 0, 2);
            product(globex, "Secret", "100.00", 0, 50);

            mvc.perform(get(summaryPath(acme)).header(AUTH, TestTokens.bearer(jwtProperties, agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productCount").value(1))
                    .andExpect(jsonPath("$.totalUnits").value(2))
                    .andExpect(jsonPath("$.stockValue").value(2.00));
        }

        @Test
        void aNonMemberGetsANotFound() throws Exception {
            User outsider = newUser();
            Organization acme = organization("Acme");

            mvc.perform(get(summaryPath(acme)).header(AUTH, TestTokens.bearer(jwtProperties, outsider)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");

            mvc.perform(get(summaryPath(acme))).andExpect(status().isUnauthorized());
        }
    }
}
