package com.connecta.backend.auth.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.auth.service.PasswordService;
import com.connecta.backend.auth.service.PasswordUnchangedException;
import com.connecta.backend.auth.service.WrongPasswordException;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class PasswordControllerIntegrationTests {

    static final UUID SESSION = UUID.randomUUID();
    static final String VALID = "{\"currentPassword\":\"current-password\",\"newPassword\":\"brand-new-password\"}";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties props;

    @MockitoBean
    PasswordService passwords;

    final User jane = TestUsers.user("jane@example.com", Roles.AGENT);

    private MockHttpServletRequestBuilder change(String body) {
        return put("/api/auth/password")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(props, jane, SESSION))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    void changesThePasswordOfTheCallerAndKeepsTheCurrentSession() throws Exception {
        mvc.perform(change(VALID)).andExpect(status().isNoContent());

        verify(passwords).change(jane.getId(), SESSION, "current-password", "brand-new-password");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(put("/api/auth/password").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesTheNewPassword() throws Exception {
        mvc.perform(change("{\"currentPassword\":\"current-password\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    void requiresTheCurrentPassword() throws Exception {
        mvc.perform(change("{\"currentPassword\":\"\",\"newPassword\":\"brand-new-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").exists());
    }

    @Test
    void aWrongCurrentPasswordIs422NotAnExpiredLogin() throws Exception {
        doThrow(new WrongPasswordException()).when(passwords).change(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        mvc.perform(change(VALID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("The current password is incorrect"));
    }

    @Test
    void anUnchangedPasswordIs422() throws Exception {
        doThrow(new PasswordUnchangedException()).when(passwords).change(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        mvc.perform(change(VALID)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aDisabledAccountIs401() throws Exception {
        doThrow(new AccountNotAvailableException()).when(passwords).change(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        mvc.perform(change(VALID)).andExpect(status().isUnauthorized());
    }
}
