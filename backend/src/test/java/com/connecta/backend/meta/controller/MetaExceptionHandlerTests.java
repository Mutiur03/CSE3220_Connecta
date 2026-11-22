package com.connecta.backend.meta.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.connecta.backend.meta.service.InvalidOAuthStateException;
import com.connecta.backend.meta.service.MetaAuthorizationFailedException;
import com.connecta.backend.meta.service.MetaAuthorizationService;
import com.connecta.backend.meta.service.MetaNotConfiguredException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

/** Each Meta failure maps to its status with a generic body. Standalone MockMvc: no database or context. */
class MetaExceptionHandlerTests {

    private final MetaAuthorizationService service = mock(MetaAuthorizationService.class);
    private final MockMvc mvc = standaloneSetup(new MetaOAuthCallbackController(service))
            .setControllerAdvice(new MetaExceptionHandler())
            .build();

    private static final String CALLBACK = MetaAuthorizationService.CALLBACK_PATH + "?code=c&state=s";

    @Nested
    class Mapping {

        @Test
        void notConfiguredIs503() throws Exception {
            when(service.completeAuthorization(anyString(), anyString())).thenThrow(new MetaNotConfiguredException());

            mvc.perform(get(CALLBACK))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.detail").value("Meta is not configured on this server"));
        }

        @Test
        void aBadStateIs400() throws Exception {
            when(service.completeAuthorization(anyString(), anyString())).thenThrow(new InvalidOAuthStateException());

            mvc.perform(get(CALLBACK))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Invalid or expired authorization state"));
        }

        @Test
        void aMetaRefusalIs502WithAGenericBody() throws Exception {
            when(service.completeAuthorization(anyString(), anyString()))
                    .thenThrow(new MetaAuthorizationFailedException("Invalid verification code from Graph"));

            mvc.perform(get(CALLBACK))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.detail").value("Meta could not complete the authorization. Try again."));
        }
    }
}
