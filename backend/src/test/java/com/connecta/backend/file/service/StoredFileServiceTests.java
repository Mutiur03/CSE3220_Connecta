package com.connecta.backend.file.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.PublicUrlProperties;
import com.connecta.backend.file.domain.StoredFile;
import com.connecta.backend.file.repository.StoredFileRepository;
import com.connecta.backend.support.TestUsers;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoredFileServiceTests {

    private static final byte[] BYTES = {1, 2, 3};

    @Mock
    StoredFileRepository files;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();

    private StoredFileService service(String publicBaseUrl) {
        return new StoredFileService(files, new PublicUrlProperties(publicBaseUrl));
    }

    @Nested
    class Store {

        @Test
        void savesTheBytesTypeAndNameForTheOrganizationAndReturnsTheId() {
            when(files.save(any(StoredFile.class))).thenAnswer(call -> {
                StoredFile file = call.getArgument(0);
                TestUsers.set(file, "id", fileId);
                return file;
            });

            assertEquals(fileId, service("https://api.example.com").store(organizationId, BYTES, "image/png",
                    "cat.png"));

            ArgumentCaptor<StoredFile> saved = ArgumentCaptor.forClass(StoredFile.class);
            verify(files).save(saved.capture());
            assertEquals(organizationId, saved.getValue().getOrganizationId());
            assertArrayEquals(BYTES, saved.getValue().getData());
            assertEquals(3, saved.getValue().getSizeBytes());
            assertEquals("image/png", saved.getValue().getContentType());
            assertEquals("cat.png", saved.getValue().getFileName());
        }
    }

    @Nested
    class SafeFileName {

        @Test
        void keepsOnlyTheLastPathSegment() {
            assertEquals("cat.png", StoredFileService.safeFileName("C:\\Users\\ann\\cat.png"));
            assertEquals("cat.png", StoredFileService.safeFileName("../../etc/cat.png"));
        }

        @Test
        void dropsControlCharactersSoNoHeaderCanBeInjected() {
            assertEquals("a.pdfX-Evil: 1", StoredFileService.safeFileName("a.pdf\r\nX-Evil: 1"));
        }

        @Test
        void cutsALongNameToTheColumn() {
            assertEquals(StoredFile.FILE_NAME_MAX_LENGTH, StoredFileService.safeFileName("a".repeat(400)).length());
        }

        @Test
        void aMissingOrBlankNameIsNull() {
            assertNull(StoredFileService.safeFileName(null));
            assertNull(StoredFileService.safeFileName("  "));
            assertNull(StoredFileService.safeFileName("dir/"));
        }
    }

    @Nested
    class PublicUrl {

        @Test
        void isTheAbsoluteFilesPathUnderThePublicBaseUrl() {
            assertEquals("https://api.example.com/api/files/" + fileId,
                    service("https://api.example.com/").publicUrl(fileId));
        }

        @Test
        void aBlankBaseUrlIsRefused() {
            assertThrows(PublicBaseUrlMissingException.class, () -> service(" ").publicUrl(fileId));
            assertThrows(PublicBaseUrlMissingException.class, () -> service(null).publicUrl(fileId));
        }
    }

    @Nested
    class Load {

        @Test
        void returnsTheStoredContent() {
            when(files.findById(fileId)).thenReturn(Optional.of(new StoredFile(organizationId, BYTES,
                    "application/pdf", "a.pdf")));

            StoredFileContent content = service("").load(fileId);

            assertEquals("application/pdf", content.contentType());
            assertEquals("a.pdf", content.fileName());
            assertArrayEquals(BYTES, content.bytes());
        }

        @Test
        void anUnknownIdIsNotFound() {
            when(files.findById(fileId)).thenReturn(Optional.empty());

            assertThrows(StoredFileNotFoundException.class, () -> service("").load(fileId));
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesScopedToTheOrganization() {
            service("").delete(organizationId, fileId);

            verify(files).deleteByIdAndOrganizationId(fileId, organizationId);
        }
    }
}
