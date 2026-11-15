package com.connecta.backend.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.auth.service.InvalidCredentialsException;
import com.connecta.backend.auth.service.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AuthExceptionHandlerTests {

    @RestController
    static class ThrowingController {

        @GetMapping("/email-used")
        void emailUsed() {
            throw new EmailAlreadyUsedException();
        }

        @GetMapping("/bad-token")
        void badToken() {
            throw new InvalidTokenException();
        }

        @GetMapping("/no-account")
        void noAccount() {
            throw new AccountNotAvailableException();
        }

        @GetMapping("/bad-credentials")
        void badCredentials() {
            throw new InvalidCredentialsException();
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void emailAlreadyUsedIs409() throws Exception {
        mvc.perform(get("/email-used"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(new EmailAlreadyUsedException().getMessage()));
    }

    @Test
    void invalidTokenIs401() throws Exception {
        mvc.perform(get("/bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired refresh token"));
    }

    @Test
    void accountNotAvailableIs401() throws Exception {
        mvc.perform(get("/no-account"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Account is not available"));
    }

    @Test
    void invalidCredentialsIs401WithOneMessageForUnknownUserAndWrongPassword() throws Exception {
        mvc.perform(get("/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }
}
