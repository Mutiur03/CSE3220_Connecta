package com.connecta.backend.auth.controller;

import com.connecta.backend.auth.dto.ClientInfo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.auth.dto.LoginRequest;
import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.TokenResponse;
import com.connecta.backend.auth.dto.UserResponse;
import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.auth.service.AuthService;
import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.auth.service.InvalidCredentialsException;
import com.connecta.backend.auth.service.InvalidTokenException;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class AuthControllerIntegrationTests {

    private static final String VALID_REGISTER =
            "{\"email\":\"jane@example.com\",\"password\":\"correct-password\",\"fullName\":\"Jane\"}";
    private static final String VALID_LOGIN = "{\"email\":\"jane@example.com\",\"password\":\"correct-password\"}";
    private static final String REFRESH_BODY = "{\"refreshToken\":\"some-refresh-token\"}";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties jwtProperties;

    @MockitoBean
    AuthService authService;

    private static MockHttpServletRequestBuilder json(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static UserResponse jane() {
        return new UserResponse(UUID.randomUUID(), "jane@example.com", "Jane", Set.of("AGENT"));
    }

    private static TokenResponse tokens() {
        return new TokenResponse("Bearer", "access-token", 900, "refresh-token", jane());
    }

    @Nested
    class Register {

        @Test
        void registerReturns201WithTheUserAndNoPassword() throws Exception {
            when(authService.register(any(RegisterRequest.class))).thenReturn(jane());

            mvc.perform(json("/api/auth/register", VALID_REGISTER))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("jane@example.com"))
                    .andExpect(jsonPath("$.roles[0]").value("AGENT"))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        void registerValidatesInput() throws Exception {
            mvc.perform(json("/api/auth/register", "{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists())
                    .andExpect(jsonPath("$.errors.fullName").exists());
        }

        @Test
        void registerRejectsPasswordsOverBcryptLimit() throws Exception {
            String longPassword = "x".repeat(73);
            mvc.perform(json("/api/auth/register",
                            "{\"email\":\"a@b.co\",\"password\":\"" + longPassword + "\",\"fullName\":\"Jane\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        @Test
        void duplicateEmailReturns409() throws Exception {
            when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyUsedException());

            mvc.perform(json("/api/auth/register", VALID_REGISTER))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

    }

    @Nested
    class Login {

        @Test
        void loginReturnsBothTokensAndTheUser() throws Exception {
            when(authService.login(any(LoginRequest.class), any(ClientInfo.class))).thenReturn(tokens());

            mvc.perform(json("/api/auth/login", VALID_LOGIN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.user.email").value("jane@example.com"));
        }

        @Test
        void badCredentialsReturn401() throws Exception {
            when(authService.login(any(LoginRequest.class), any(ClientInfo.class))).thenThrow(new InvalidCredentialsException());

            mvc.perform(json("/api/auth/login", VALID_LOGIN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }

        @Test
        void loginRequiresBothFields() throws Exception {
            mvc.perform(json("/api/auth/login", "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists());
        }

    }

    @Nested
    class Refresh {

        @Test
        void refreshReturnsNewTokens() throws Exception {
            when(authService.refresh(eq("some-refresh-token"), any(ClientInfo.class))).thenReturn(tokens());

            mvc.perform(json("/api/auth/refresh", REFRESH_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
        }

        @Test
        void invalidRefreshTokenReturns401() throws Exception {
            when(authService.refresh(any(), any(ClientInfo.class))).thenThrow(new InvalidTokenException());

            mvc.perform(json("/api/auth/refresh", REFRESH_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid or expired refresh token"));
        }

        @Test
        void refreshRequiresAToken() throws Exception {
            mvc.perform(json("/api/auth/refresh", "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.refreshToken").exists());
        }

    }

    @Nested
    class Logout {

        @Test
        void logoutReturns204AndRevokesTheToken() throws Exception {
            doNothing().when(authService).logout("some-refresh-token");

            mvc.perform(json("/api/auth/logout", REFRESH_BODY))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
            verify(authService).logout("some-refresh-token");
        }

    }

    @Nested
    class CurrentUser {

        @Test
        void meReturnsTheUserOfTheAccessToken() throws Exception {
            User user = TestUsers.user("jane@example.com", Roles.AGENT);
            when(authService.currentUser(user.getId().toString())).thenReturn(jane());

            mvc.perform(get("/api/auth/me").header("Authorization", TestTokens.bearer(jwtProperties, user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("jane@example.com"));
        }

        @Test
        void meReturns401ProblemJsonWhenTheAccountIsNotAvailable() throws Exception {
            User user = TestUsers.user("jane@example.com", Roles.AGENT);
            when(authService.currentUser(user.getId().toString())).thenThrow(new AccountNotAvailableException());

            mvc.perform(get("/api/auth/me").header("Authorization", TestTokens.bearer(jwtProperties, user)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("Account is not available"));
        }

        @Test
        void loginAndRefreshIgnoreAStaleAccessToken() throws Exception {
            String expired = TestTokens.expiredBearer(jwtProperties, TestUsers.user("jane@example.com", Roles.AGENT));
            when(authService.login(any(LoginRequest.class), any(ClientInfo.class))).thenReturn(tokens());
            when(authService.refresh(eq("some-refresh-token"), any(ClientInfo.class))).thenReturn(tokens());

            mvc.perform(json("/api/auth/login", VALID_LOGIN).header("Authorization", expired))
                    .andExpect(status().isOk());
            mvc.perform(json("/api/auth/refresh", REFRESH_BODY).header("Authorization", expired))
                    .andExpect(status().isOk());
        }

        @Test
        void meRequiresAToken() throws Exception {
            mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        }

        @Test
        void meRejectsAnExpiredToken() throws Exception {
            User user = TestUsers.user("jane@example.com", Roles.AGENT);

            mvc.perform(get("/api/auth/me").header("Authorization", TestTokens.expiredBearer(jwtProperties, user)))
                    .andExpect(status().isUnauthorized());
        }

    }
}
