package com.connecta.backend.setup.controller;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The first-run setup against real PostgreSQL (Flyway runs the migrations). Both endpoints are public by
 * design; the guarantee under test is that POST works exactly once. Skipped without Docker.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class SetupControllerIntegrationTests extends SharedPostgres {

    private static final String STATUS = "/api/setup/status";
    private static final String SETUP = "/api/setup";
    private static final String OWNER_BODY =
            "{\"email\":\"boss@example.com\",\"password\":\"correct-password\",\"fullName\":\"Boss\"}";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @BeforeEach
    void freshSystem() {
        for (String table : new String[] {"invitations", "memberships", "refresh_tokens", "system_setup",
            "user_roles", "users"}) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    class Status {

        @Test
        void aFreshSystemNeedsSetupAndNoTokenIsRequired() throws Exception {
            mvc.perform(get(STATUS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.setupRequired").value(true));
        }

        @Test
        void aBadTokenDoesNotBlockThePublicEndpoint() throws Exception {
            mvc.perform(get(STATUS).header("Authorization", "Bearer garbage"))
                    .andExpect(status().isOk());
        }

        @Test
        void setupIsNotRequiredAfterTheOwnerExists() throws Exception {
            mvc.perform(json(post(SETUP), OWNER_BODY)).andExpect(status().isCreated());

            mvc.perform(get(STATUS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.setupRequired").value(false));
        }
    }

    @Nested
    class CompleteSetup {

        @Test
        void createsTheOwnerWithoutPasswordDataAndRecordsTheSetup() throws Exception {
            mvc.perform(json(post(SETUP), OWNER_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("boss@example.com"))
                    .andExpect(jsonPath("$.roles[0]").value(Roles.OWNER))
                    .andExpect(jsonPath("$.roles.length()").value(1))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());

            User owner = users.findByEmail("boss@example.com").orElseThrow();
            assertEquals(java.util.Set.of(Roles.OWNER), owner.getRoleNames());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM system_setup", Integer.class));
            assertEquals(owner.getId(), jdbc.queryForObject("SELECT owner_id FROM system_setup", java.util.UUID.class));
        }

        @Test
        void aSecondSetupIsAConflictAndCreatesNothing() throws Exception {
            mvc.perform(json(post(SETUP), OWNER_BODY)).andExpect(status().isCreated());

            mvc.perform(json(post(SETUP),
                            "{\"email\":\"other@example.com\",\"password\":\"correct-password\",\"fullName\":\"Other\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Setup is already complete"));
            assertEquals(1L, users.count());
        }

        @Test
        void anExistingEmailIsAConflict() throws Exception {
            User existing = new User("boss@example.com", "hash", "Boss");
            existing.addRole(roles.findByName(Roles.AGENT).orElseThrow());
            users.saveAndFlush(existing);

            mvc.perform(json(post(SETUP), OWNER_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM system_setup", Integer.class));
        }

        @Test
        void invalidInputIsABadRequest() throws Exception {
            mvc.perform(json(post(SETUP), "{\"email\":\"nope\",\"password\":\"short\",\"fullName\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists())
                    .andExpect(jsonPath("$.errors.fullName").exists());
        }
    }

    @Nested
    class SignupAfterSetup {

        private static final String REGISTER = "/api/auth/register";
        private static final String JANE =
                "{\"email\":\"jane@example.com\",\"password\":\"correct-password\",\"fullName\":\"Jane\"}";

        @Test
        void signupIsClosedUntilTheOwnerExists() throws Exception {
            mvc.perform(json(post(REGISTER), JANE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Setup is required before accounts can be created"));
            assertEquals(0L, users.count());
        }

        @Test
        void signupMakesAnAdminOnceTheOwnerExists() throws Exception {
            mvc.perform(json(post(SETUP), OWNER_BODY)).andExpect(status().isCreated());

            mvc.perform(json(post(REGISTER), JANE))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roles[0]").value(Roles.ADMIN))
                    .andExpect(jsonPath("$.roles.length()").value(1));
        }
    }
}
