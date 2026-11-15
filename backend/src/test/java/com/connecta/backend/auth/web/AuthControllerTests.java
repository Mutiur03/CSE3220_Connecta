package com.connecta.backend.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.auth.dto.LoginRequest;
import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.TokenResponse;
import com.connecta.backend.auth.dto.UserResponse;
import com.connecta.backend.auth.service.AuthService;
import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.auth.service.InvalidCredentialsException;
import com.connecta.backend.auth.service.InvalidTokenException;
import com.connecta.backend.support.MockRepositories;
import java.util.Set;
import java.util.UUID;
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
class AuthControllerTests {

    private static final String VALID_REGISTER =
            "{\"email\":\"jane@example.com\",\"password\":\"correct-password\",\"fullName\":\"Jane\"}";
    private static final String VALID_LOGIN = "{\"email\":\"jane@example.com\",\"password\":\"correct-password\"}";
    private static final String REFRESH_BODY = "{\"refreshToken\":\"some-refresh-token\"}";

    @Autowired
    MockMvc mvc;

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

    @Test
    void loginReturnsBothTokensAndTheUser() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(tokens());

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
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

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

    @Test
    void refreshReturnsNewTokens() throws Exception {
        when(authService.refresh("some-refresh-token")).thenReturn(tokens());

        mvc.perform(json("/api/auth/refresh", REFRESH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void invalidRefreshTokenReturns401() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidTokenException());

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

    @Test
    void logoutReturns204AndRevokesTheToken() throws Exception {
        doNothing().when(authService).logout("some-refresh-token");

        mvc.perform(json("/api/auth/logout", REFRESH_BODY))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(authService).logout("some-refresh-token");
    }
}
