package com.connecta.backend.setup.dto;

/** Whether the first-run setup still has to create the system owner. */
public record SetupStatusResponse(boolean setupRequired) {
}
