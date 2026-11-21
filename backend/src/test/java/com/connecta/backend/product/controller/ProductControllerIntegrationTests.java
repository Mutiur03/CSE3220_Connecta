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
}
