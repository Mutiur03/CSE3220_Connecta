package com.connecta.backend.file.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.file.service.StoredFileContent;
import com.connecta.backend.file.service.StoredFileNotFoundException;
import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.support.MockRepositories;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The public read: no token needed, the stored type and safe headers, 404 for an unknown id. */
@SpringBootTest
@AutoConfigureMockMvc
@MockRepositories
class StoredFileControllerIntegrationTests {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1'};

    private final UUID fileId = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    StoredFileService files;

    @Test
    void anyoneReadsTheBytesWithTheStoredTypeAndSafeHeaders() throws Exception {
        when(files.load(fileId)).thenReturn(new StoredFileContent("application/pdf", "Rechnung März.pdf", PDF));

        mvc.perform(get("/api/files/" + fileId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''Rechnung%20M%C3%A4rz.pdf")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")));
    }

    @Test
    void aFileWithoutANameIsServedInlineWithoutOne() throws Exception {
        when(files.load(fileId)).thenReturn(new StoredFileContent("image/png", null, new byte[] {1}));

        mvc.perform(get("/api/files/" + fileId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"));
    }

    @Test
    void anExpiredTokenOnThePublicPathIsIgnored() throws Exception {
        when(files.load(fileId)).thenReturn(new StoredFileContent("image/png", null, new byte[] {1}));

        mvc.perform(get("/api/files/" + fileId).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownIdIs404ProblemJson() throws Exception {
        when(files.load(fileId)).thenThrow(new StoredFileNotFoundException());

        mvc.perform(get("/api/files/" + fileId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("File not found"));
    }
}
