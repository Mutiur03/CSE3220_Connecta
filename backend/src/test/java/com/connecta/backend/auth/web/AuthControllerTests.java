package com.connecta.backend.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.auth.dto.LoginRequest;
import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.UserResponse;
import com.connecta.backend.auth.service.AuthService;
import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.auth.service.InvalidCredentialsException;
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

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthService authService;

    private static MockHttpServletRequestBuilder json(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static final String VALID_REGISTER =
            "{\"email\":\"jane@example.com\",\"password\":\"correct-password\",\"fullName\":\"Jane\"}";

    @Test
    void registerReturns201WithTheUserAndNoPassword() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(UUID.randomUUID(), "jane@example.com", "Jane", Set.of("AGENT")));

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
    void loginReturnsTheUser() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new UserResponse(UUID.randomUUID(), "jane@example.com", "Jane", Set.of("AGENT")));

        mvc.perform(json("/api/auth/login", "{\"email\":\"jane@example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void badCredentialsReturn401() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mvc.perform(json("/api/auth/login", "{\"email\":\"jane@example.com\",\"password\":\"wrong-password\"}"))
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
