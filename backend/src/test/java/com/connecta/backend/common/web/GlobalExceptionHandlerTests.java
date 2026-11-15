package com.connecta.backend.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTests {

    record Payload(@NotBlank(message = "must not be blank") @Size(max = 3, message = "too long") String name) {
    }

    @RestController
    static class StubController {

        @PostMapping("/stub")
        String create(@Valid @RequestBody Payload payload) {
            return payload.name();
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder json(String body) {
        return post("/stub").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    class ValidationFailures {

        @Test
        void returns400ProblemJsonWithAMapOfFieldErrors() throws Exception {
            mvc.perform(json("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.name").value("must not be blank"));
        }

        @Test
        void reportsTheSizeRuleOfTheBadField() throws Exception {
            mvc.perform(json("{\"name\":\"abcdef\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").value("too long"));
        }
    }

    @Nested
    class OtherSpringMvcErrors {

        @Test
        void malformedJsonReturns400WithoutLeakingParserDetails() throws Exception {
            mvc.perform(json("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(content().string(not(containsString("Jackson"))));
        }

        @Test
        void wrongMethodReturns405ProblemJson() throws Exception {
            mvc.perform(get("/stub"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").value(405));
        }

        @Test
        void unsupportedMediaTypeReturns415ProblemJson() throws Exception {
            mvc.perform(post("/stub").contentType(MediaType.TEXT_PLAIN).content("x"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.status").value(415));
        }
    }
}
