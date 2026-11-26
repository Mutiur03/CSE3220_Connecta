package com.connecta.backend.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.auth.domain.RefreshToken;
import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.realtime.service.RealtimeSessionRegistry;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketSession;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * /api/admin/users against real PostgreSQL: OWNER only. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class AdminUserControllerIntegrationTests {

    private static final String PATH = "/api/admin/users";
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
    RealtimeSessionRegistry registry;

    @Autowired
    RefreshTokenRepository refreshTokens;

    private User userWith(String roleName) {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(roles.findByName(roleName).orElseThrow());
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    private String owner() {
        return bearer(userWith(Roles.OWNER));
    }

    private long activeTokens(User user) {
        return refreshTokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), Instant.now(),
                Pageable.unpaged()).getTotalElements();
    }

    private static String enabled(boolean value) {
        return "{\"enabled\":" + value + "}";
    }

    @Test
    void theOwnerListsUsersSortedByEmailWithoutSecrets() throws Exception {
        userWith(Roles.AGENT);

        mvc.perform(get(PATH).param("size", "1").header(AUTH, owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].roles").isArray())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void theOwnersOwnAccountIsFirstOnPageZeroAndNeverRepeats() throws Exception {
        User owner = new User("zzz-" + UUID.randomUUID() + "@example.com", "hash", "Owner");
        owner.addRole(roles.findByName(Roles.OWNER).orElseThrow());
        users.saveAndFlush(owner);
        userWith(Roles.AGENT);
        String token = bearer(owner);

        mvc.perform(get(PATH).param("size", "1").header(AUTH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(owner.getId().toString()));
        mvc.perform(get(PATH).param("page", "1").param("size", "1").header(AUTH, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(org.hamcrest.Matchers.not(owner.getId().toString())));
    }

    @Test
    void anAdminAndAnAgentAreForbiddenFromEveryEndpoint() throws Exception {
        User target = userWith(Roles.AGENT);
        for (String role : new String[] {Roles.ADMIN, Roles.AGENT}) {
            String token = bearer(userWith(role));
            mvc.perform(get(PATH).header(AUTH, token)).andExpect(status().isForbidden());
            mvc.perform(patch(PATH + "/" + target.getId()).header(AUTH, token)
                    .contentType(MediaType.APPLICATION_JSON).content(enabled(false)))
                    .andExpect(status().isForbidden());
        }
        assertTrue(users.findById(target.getId()).orElseThrow().isEnabled());
    }

    @Test
    void anAnonymousCallerIsUnauthorized() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(patch(PATH + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(enabled(false))).andExpect(status().isUnauthorized());
    }

    @Test
    void pageBoundsAreValidated() throws Exception {
        mvc.perform(get(PATH).param("size", "101").header(AUTH, owner())).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("page", "-1").header(AUTH, owner())).andExpect(status().isBadRequest());
    }

    @Test
    void disablingAUserReturnsThemDisabledAndTheyCanBeEnabledAgain() throws Exception {
        User target = userWith(Roles.AGENT);
        String path = PATH + "/" + target.getId();

        mvc.perform(patch(path).header(AUTH, owner()).contentType(MediaType.APPLICATION_JSON).content(enabled(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId().toString()))
                .andExpect(jsonPath("$.enabled").value(false));
        assertFalse(users.findById(target.getId()).orElseThrow().isEnabled());

        mvc.perform(patch(path).header(AUTH, owner()).contentType(MediaType.APPLICATION_JSON).content(enabled(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void disablingTwiceIsIdempotent() throws Exception {
        String path = PATH + "/" + userWith(Roles.AGENT).getId();
        String token = owner();

        for (int i = 0; i < 2; i++) {
            mvc.perform(patch(path).header(AUTH, token).contentType(MediaType.APPLICATION_JSON).content(enabled(false)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));
        }
    }

    @Test
    void disablingRevokesRefreshTokens() throws Exception {
        User target = userWith(Roles.AGENT);
        Instant now = Instant.now();
        refreshTokens.saveAndFlush(new RefreshToken(target, "hash-" + UUID.randomUUID(), now.plusSeconds(3600), now,
                UUID.randomUUID(), null, null, null));
        assertEquals(1, activeTokens(target));

        mvc.perform(patch(PATH + "/" + target.getId()).header(AUTH, owner())
                .contentType(MediaType.APPLICATION_JSON).content(enabled(false)))
                .andExpect(status().isOk());

        assertEquals(0, activeTokens(target));
    }

    @Test
    void disablingClosesTheUsersOpenSessionWithCode4003() throws Exception {
        User target = userWith(Roles.AGENT);
        WebSocketSession open = mock(WebSocketSession.class);
        when(open.getId()).thenReturn("session-" + target.getId());
        when(open.isOpen()).thenReturn(true);
        registry.register(open, UUID.randomUUID(), target.getId());

        mvc.perform(patch(PATH + "/" + target.getId()).header(AUTH, owner())
                .contentType(MediaType.APPLICATION_JSON).content(enabled(false)))
                .andExpect(status().isOk());

        verify(open).close(RealtimeSessionRegistry.ACCESS_REVOKED);
    }

    @Test
    void anOwnerCannotBeDisabledNotEvenByThemself() throws Exception {
        User other = userWith(Roles.OWNER);
        User self = userWith(Roles.OWNER);

        mvc.perform(patch(PATH + "/" + other.getId()).header(AUTH, bearer(self))
                .contentType(MediaType.APPLICATION_JSON).content(enabled(false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));
        mvc.perform(patch(PATH + "/" + self.getId()).header(AUTH, bearer(self))
                .contentType(MediaType.APPLICATION_JSON).content(enabled(false)))
                .andExpect(status().isUnprocessableContent());
        assertTrue(users.findById(self.getId()).orElseThrow().isEnabled());
    }

    @Test
    void anUnknownUserIsNotFound() throws Exception {
        mvc.perform(patch(PATH + "/" + UUID.randomUUID()).header(AUTH, owner())
                .contentType(MediaType.APPLICATION_JSON).content(enabled(false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void aMissingEnabledFlagIsRejected() throws Exception {
        mvc.perform(patch(PATH + "/" + userWith(Roles.AGENT).getId()).header(AUTH, owner())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
