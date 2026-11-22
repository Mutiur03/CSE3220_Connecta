package com.connecta.backend.meta.dto;

import com.connecta.backend.common.dto.PageResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The Facebook Page ids the admin chose to connect. Each must be part of the Meta grant. */
public record ConnectPagesRequest(
        @NotEmpty @Size(max = PageResponse.MAX_SIZE) List<@NotBlank @Size(max = PAGE_ID_MAX_LENGTH) String> pageIds) {

    static final int PAGE_ID_MAX_LENGTH = 64;
}
