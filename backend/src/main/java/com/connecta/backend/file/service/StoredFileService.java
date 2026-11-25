package com.connecta.backend.file.service;

import com.connecta.backend.config.PublicUrlProperties;
import com.connecta.backend.file.domain.StoredFile;
import com.connecta.backend.file.repository.StoredFileRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores files in PostgreSQL and serves them at a public, unguessable URL ({@code /api/files/{id}}), so an
 * outside system such as Meta can fetch them. Reusable by any feature: it knows nothing about messages. Callers
 * validate type and size first; this class only keeps the bytes, the declared type and a safe file name.
 */
@Service
@RequiredArgsConstructor
public class StoredFileService {

    static final String FILES_PATH = "/api/files/";

    private final StoredFileRepository files;
    private final PublicUrlProperties publicUrls;

    /** Stores the bytes for the organization and returns the new file's id. */
    @Transactional
    public UUID store(UUID organizationId, byte[] bytes, String contentType, String fileName) {
        return files.save(new StoredFile(organizationId, bytes, contentType, safeFileName(fileName))).getId();
    }

    /**
     * The absolute public URL of the file.
     *
     * @throws PublicBaseUrlMissingException PUBLIC_BASE_URL is not set, so no outside system could fetch it
     */
    public String publicUrl(UUID id) {
        String base = publicUrls.baseUrl();
        if (base.isEmpty()) {
            throw new PublicBaseUrlMissingException();
        }
        return base + FILES_PATH + id;
    }

    /** True when PUBLIC_BASE_URL is set, so {@link #publicUrl} can build links. */
    public boolean hasPublicBaseUrl() {
        return !publicUrls.baseUrl().isEmpty();
    }

    /**
     * The file for the public read. Not scoped by organization on purpose: the random id is the access check.
     *
     * @throws StoredFileNotFoundException no file has this id
     */
    @Transactional(readOnly = true)
    public StoredFileContent load(UUID id) {
        return files.findById(id)
                .map(file -> new StoredFileContent(file.getContentType(), file.getFileName(), file.getData()))
                .orElseThrow(StoredFileNotFoundException::new);
    }

    /** Deletes the organization's file. Another organization's id, or an unknown one, deletes nothing. */
    @Transactional
    public void delete(UUID organizationId, UUID id) {
        files.deleteByIdAndOrganizationId(id, organizationId);
    }

    /** The last path segment without control characters, cut to the column; null when nothing is left. */
    public static String safeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            return null;
        }
        return name.length() <= StoredFile.FILE_NAME_MAX_LENGTH ? name
                : name.substring(0, StoredFile.FILE_NAME_MAX_LENGTH);
    }
}
