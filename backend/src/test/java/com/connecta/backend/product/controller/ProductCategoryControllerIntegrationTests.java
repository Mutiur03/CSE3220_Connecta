package com.connecta.backend.product.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.product.repository.ProductCategoryRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The product category endpoints against real PostgreSQL (Flyway runs the migrations). Each write has an
 * allowed role and a denied role, plus 401, validation, the duplicate 409 and the cross-tenant 404.
 * Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ProductCategoryControllerIntegrationTests {

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
    ProductCategoryRepository categories;

    private static String categoriesPath(Organization organization) {
        return "/api/organizations/" + organization.getId() + "/product-categories";
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String body(String name, String description) {
        return "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}";
    }

    private User newUser(String globalRole) {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(role(globalRole));
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

    private ProductCategory category(Organization organization, String name) {
        return categories.saveAndFlush(new ProductCategory(organization, name, "desc"));
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    /** An organization with one admin and one agent, plus a category; the admin and agent are members. */
    private record Seeded(Organization acme, User admin, User agent, ProductCategory shoes) {
    }

    private Seeded seedAcme() {
        Organization acme = organization("Acme");
        User admin = newUser(Roles.ADMIN);
        User agent = newUser(Roles.AGENT);
        join(acme, admin, Roles.ADMIN);
        join(acme, agent, Roles.AGENT);
        return new Seeded(acme, admin, agent, category(acme, "Shoes"));
    }

    @Nested
    class ListCategories {

        @Test
        void aMemberGetsOnePageOfCategories() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(get(categoriesPath(s.acme()) + "?page=0&size=20")
                            .header(AUTH, bearer(s.agent())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Shoes"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void aNonMemberGetsNotFound() throws Exception {
            Seeded s = seedAcme();
            User stranger = newUser(Roles.AGENT);

            mvc.perform(get(categoriesPath(s.acme())).header(AUTH, bearer(stranger)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anUnauthenticatedCallerGetsUnauthorized() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(get(categoriesPath(s.acme()))).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Get {

        @Test
        void aCategoryOfAnotherOrganizationIsNotFound() throws Exception {
            Seeded s = seedAcme();
            Organization other = organization("Other");
            join(other, s.admin(), Roles.ADMIN);
            ProductCategory foreign = category(other, "Bags");

            mvc.perform(get(categoriesPath(s.acme()) + "/" + foreign.getId()).header(AUTH, bearer(s.admin())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Category not found"));
        }
    }

    @Nested
    class Create {

        @Test
        void anAdminCreatesACategoryWithLocation() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(json(post(categoriesPath(s.acme())).header(AUTH, bearer(s.admin())),
                            body("Bags", "Leather goods")))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.name").value("Bags"))
                    .andExpect(jsonPath("$.organizationId").value(s.acme().getId().toString()));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(json(post(categoriesPath(s.acme())).header(AUTH, bearer(s.agent())),
                            body("Bags", "")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aBlankNameIsRejectedWithFieldErrors() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(json(post(categoriesPath(s.acme())).header(AUTH, bearer(s.admin())), body(" ", "")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        @Test
        void aDuplicateNameInTheSameOrganizationIsConflict() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(json(post(categoriesPath(s.acme())).header(AUTH, bearer(s.admin())), body("Shoes", "")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("A product category with this name already exists"));
        }
    }

    @Nested
    class Update {

        @Test
        void anAdminReplacesTheCategory() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(json(put(categoriesPath(s.acme()) + "/" + s.shoes().getId())
                            .header(AUTH, bearer(s.admin())), body("Footwear", "All shoes")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Footwear"));
        }

        @Test
        void anAgentIsForbidden() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(json(put(categoriesPath(s.acme()) + "/" + s.shoes().getId())
                            .header(AUTH, bearer(s.agent())), body("Footwear", "")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Delete {

        @Test
        void anAdminDeletesTheCategoryAndItIsGone() throws Exception {
            Seeded s = seedAcme();
            String path = categoriesPath(s.acme()) + "/" + s.shoes().getId();

            mvc.perform(delete(path).header(AUTH, bearer(s.admin()))).andExpect(status().isNoContent());

            mvc.perform(get(path).header(AUTH, bearer(s.admin()))).andExpect(status().isNotFound());
            assertFalse(categories.existsById(s.shoes().getId()));
        }

        @Test
        void anAgentIsForbiddenAndNothingIsDeleted() throws Exception {
            Seeded s = seedAcme();

            mvc.perform(delete(categoriesPath(s.acme()) + "/" + s.shoes().getId())
                            .header(AUTH, bearer(s.agent())))
                    .andExpect(status().isForbidden());
            assertTrue(categories.existsById(s.shoes().getId()));
        }
    }
}
