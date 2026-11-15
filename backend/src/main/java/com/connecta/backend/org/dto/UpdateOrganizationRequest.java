package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.Organization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only the name can change; the slug is part of the organization's identity and is fixed. */
public record UpdateOrganizationRequest(
        @NotBlank @Size(max = Organization.NAME_MAX_LENGTH) String name) {
}
