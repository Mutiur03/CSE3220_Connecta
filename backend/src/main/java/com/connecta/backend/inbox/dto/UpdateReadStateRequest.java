package com.connecta.backend.inbox.dto;

import jakarta.validation.constraints.NotNull;

/** Marks a conversation read ({@code unread: false}) or back to unread ({@code unread: true}). */
public record UpdateReadStateRequest(@NotNull Boolean unread) {
}
