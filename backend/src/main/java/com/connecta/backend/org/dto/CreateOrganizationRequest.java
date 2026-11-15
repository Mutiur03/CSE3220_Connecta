package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.Organization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = Organization.NAME_MAX_LENGTH) String name,
        @NotBlank @Size(max = Organization.SLUG_MAX_LENGTH) @Pattern(regexp = Organization.SLUG_PATTERN) String slug) {
}
