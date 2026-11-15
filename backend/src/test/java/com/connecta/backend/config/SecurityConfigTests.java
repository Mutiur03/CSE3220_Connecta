package com.connecta.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTests {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired
    MockMvc mvc;

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
