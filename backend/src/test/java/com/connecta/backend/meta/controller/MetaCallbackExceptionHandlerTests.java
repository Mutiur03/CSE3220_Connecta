package com.connecta.backend.meta.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.connecta.backend.config.FrontendProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.MetaAuthorization;
import com.connecta.backend.meta.service.InvalidOAuthStateException;
import com.connecta.backend.meta.service.MetaAuthorizationDeniedException;
import com.connecta.backend.meta.service.MetaAuthorizationFailedException;
import com.connecta.backend.meta.service.MetaAuthorizationService;
import com.connecta.backend.meta.service.MetaNotConfiguredException;
import com.connecta.backend.meta.service.OAuthStateSigner;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The callbacks answer with a redirect to the web app, never JSON. Standalone MockMvc: no database or context.
 * Success goes to the Integrations page; each failure goes there with a metaError code, or to /organizations when
 * the state names no organization.
 */
class MetaCallbackExceptionHandlerTests {

    private static final String BASE = "http://localhost:3000";
    private static final String CALLBACK = MetaAuthorizationService.CALLBACK_PATH;
    private static final String SECRET = "app-secret";

    private final MetaAuthorizationService service = mock(MetaAuthorizationService.class);
    private final FrontendProperties frontend = new FrontendProperties(BASE);
    private final OAuthStateSigner signer = new OAuthStateSigner(
            new MetaProperties("app", SECRET, null, "config", "https://api.example.com", "unused"));
    private final MockMvc mvc = standaloneSetup(new MetaOAuthCallbackController(service, frontend))
            .setControllerAdvice(new MetaCallbackExceptionHandler(frontend, signer))
            .build();

    private final UUID organizationId = UUID.randomUUID();
    private final User admin = TestUsers.user("admin@example.com");

    /** A state that names the organization, signed as the app would. */
    private String stateFor() {
        return signer.issue(organizationId, admin.getId(), TestClock.NOW);
    }

    private MockHttpServletRequestBuilder callback(String state) {
        return get(CALLBACK).param("code", "c").param("state", state);
    }

    private String integrationsUrl(String query) {
        return BASE + "/organizations/" + organizationId + "/integrations?" + query;
    }

    @Nested
    class Success {

        @Test
        void aConnectedAdminIsSentBackToTheIntegrationsPage() throws Exception {
            Organization acme = new Organization("Acme", "acme");
            TestUsers.set(acme, "id", organizationId);
            MetaAuthorization grant = new MetaAuthorization(acme);
            when(service.completeAuthorization(anyString(), anyString())).thenReturn(grant);

            mvc.perform(callback(stateFor()))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrationsUrl("facebook=connected")));
        }
    }

    @Nested
    class Failures {

        @Test
        void aNotConfiguredServerReturnsWithAnErrorCode() throws Exception {
            when(service.completeAuthorization(anyString(), anyString())).thenThrow(new MetaNotConfiguredException());

            mvc.perform(callback(stateFor()))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrationsUrl("metaError=not_configured")));
        }

        @Test
        void aTamperedStateReturnsWithInvalidStateForTheOrganizationItNames() throws Exception {
            String forged = new OAuthStateSigner(
                    new MetaProperties("app", "another-secret", null, "config", "https://api.example.com", "unused"))
                    .issue(organizationId, admin.getId(), TestClock.NOW);
            when(service.completeAuthorization(anyString(), anyString())).thenThrow(new InvalidOAuthStateException());

            mvc.perform(callback(forged))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrationsUrl("metaError=invalid_state")));
        }

        @Test
        void aMissingStateFallsBackToTheOrganizationsPage() throws Exception {
            when(service.completeAuthorization(any(), any())).thenThrow(new InvalidOAuthStateException());

            mvc.perform(get(CALLBACK).param("code", "c"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", BASE + "/organizations?metaError=invalid_state"));
        }

        @Test
        void aCancelledLoginReturnsWithDenied() throws Exception {
            when(service.completeAuthorization(any(), any())).thenThrow(new MetaAuthorizationDeniedException());

            mvc.perform(get(CALLBACK).param("state", stateFor()))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrationsUrl("metaError=denied")));
        }

        @Test
        void aLostAdminRoleReturnsWithForbidden() throws Exception {
            when(service.completeAuthorization(anyString(), anyString()))
                    .thenThrow(new InsufficientOrganizationRoleException());

            mvc.perform(callback(stateFor()))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrationsUrl("metaError=forbidden")));
        }

        @Test
        void aMetaRefusalReturnsWithAGenericCode() throws Exception {
            when(service.completeAuthorization(anyString(), anyString()))
                    .thenThrow(new MetaAuthorizationFailedException("Invalid verification code"));

            mvc.perform(callback(stateFor()))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", integrationsUrl("metaError=meta_failed")));
        }
    }
}
