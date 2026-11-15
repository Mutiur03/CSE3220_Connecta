package com.connecta.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.auth.security.JwtProperties;
import com.connecta.backend.support.MockRepositories;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@MockRepositories
@AutoConfigureMockMvc
class SecurityConfigTests {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties jwtProperties;

    @Nested
    class Access {

        @Test
        void healthIsPublic() throws Exception {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        void otherEndpointsRequireAuth() throws Exception {
            mvc.perform(get("/anything")).andExpect(status().isUnauthorized());
        }

        @Test
        void apiDocsArePublic() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openapi").exists());
        }

        @Test
        void swaggerUiIsReachable() throws Exception {
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }

    @Nested
    class BearerAuthentication {

        private static final String PROTECTED = "/api/test/protected";
        private final User user = TestUsers.user("jane@example.com", Roles.AGENT);

        @Test
        void missingTokenGets401WithAJsonProblemAndBearerChallenge() throws Exception {
            mvc.perform(get(PROTECTED))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().exists("WWW-Authenticate"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        void validTokenIsAccepted() throws Exception {
            mvc.perform(get(PROTECTED).header("Authorization", TestTokens.bearer(jwtProperties, user)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
        }

        @Test
        void expiredTokenGets401InvalidToken() throws Exception {
            mvc.perform(get(PROTECTED).header("Authorization", TestTokens.expiredBearer(jwtProperties, user)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")));
        }

        @Test
        void tokenSignedWithAnotherSecretGets401() throws Exception {
            mvc.perform(get(PROTECTED).header("Authorization", TestTokens.forgedBearer(jwtProperties, user)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void garbageTokenGets401() throws Exception {
            mvc.perform(get(PROTECTED).header("Authorization", "Bearer not.a.jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void basicAuthIsNotAccepted() throws Exception {
            mvc.perform(get(PROTECTED).header("Authorization", "Basic YWRtaW46cGFzc3dvcmQ="))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void publicEndpointsIgnoreABadToken() throws Exception {
            mvc.perform(get("/actuator/health").header("Authorization", "Bearer not.a.jwt"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Cors {

        @Test
        void allowedOriginGetsCorsHeaders() throws Exception {
            mvc.perform(get("/actuator/health").header("Origin", ALLOWED_ORIGIN))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
        }

        @Test
        void preflightFromAllowedOriginSucceeds() throws Exception {
            mvc.perform(options("/api/anything")
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
        }

        @Test
        void otherOriginIsRejected() throws Exception {
            mvc.perform(get("/actuator/health").header("Origin", "http://evil.example"))
                    .andExpect(status().isForbidden());
        }
    }
}
