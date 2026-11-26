package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetAvailabilityRequest(
        @NotNull @Pattern(regexp = "^(AVAILABLE|AWAY)$") String status) {

    public AvailabilityStatus toStatus() {
        return AvailabilityStatus.valueOf(status);
    }
}
