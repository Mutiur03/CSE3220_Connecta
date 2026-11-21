package com.connecta.backend.org.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
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
 * GET /api/admin/organizations against real PostgreSQL: OWNER only. The OWNER is a system role, so the
 * caller needs no membership anywhere. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AdminOrganizationControllerIntegrationTests {

    private static final String PATH = "/api/admin/organizations";
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

    private User userWith(String roleName) {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(roles.findByName(roleName).orElseThrow());
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    private Organization organizationWithMembers(String name, int memberCount) {
        Organization organization = organizations.saveAndFlush(new Organization(name, "org-" + UUID.randomUUID()));
        for (int i = 0; i < memberCount; i++) {
            memberships.saveAndFlush(new Membership(organization, userWith(Roles.AGENT),
                    roles.findByName(Roles.AGENT).orElseThrow()));
        }
        return organization;
    }

    @Test
    void theOwnerSeesEveryOrganizationWithItsMemberCount() throws Exception {
        Organization crowded = organizationWithMembers("Crowded", 3);
        Organization empty = organizationWithMembers("Empty", 0);

        String body = mvc.perform(get(PATH).param("size", "100").header(AUTH, bearer(userWith(Roles.OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andReturn().getResponse().getContentAsString();

        List<Integer> crowdedCount = JsonPath.read(body, "$.content[?(@.id=='" + crowded.getId() + "')].memberCount");
        List<Integer> emptyCount = JsonPath.read(body, "$.content[?(@.id=='" + empty.getId() + "')].memberCount");
        assertEquals(List.of(3), crowdedCount);
        assertEquals(List.of(0), emptyCount);
    }

    @Test
    void pagesAreBoundedAndSortedByName() throws Exception {
        organizationWithMembers("Aardvark", 0);
        organizationWithMembers("Zebra", 0);

        mvc.perform(get(PATH).param("size", "1").header(AUTH, bearer(userWith(Roles.OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Aardvark"));
    }

    @Test
    void anAdminIsForbidden() throws Exception {
        mvc.perform(get(PATH).header(AUTH, bearer(userWith(Roles.ADMIN))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void anAgentIsForbidden() throws Exception {
        mvc.perform(get(PATH).header(AUTH, bearer(userWith(Roles.AGENT))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousCallerIsUnauthorized() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void sizeAboveTheMaximumIsRejected() throws Exception {
        mvc.perform(get(PATH).param("size", "101").header(AUTH, bearer(userWith(Roles.OWNER))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aNegativePageIsRejected() throws Exception {
        mvc.perform(get(PATH).param("page", "-1").header(AUTH, bearer(userWith(Roles.OWNER))))
                .andExpect(status().isBadRequest());
    }
}
