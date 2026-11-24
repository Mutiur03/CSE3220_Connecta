package com.connecta.backend.realtime.dto;

/** A single-use WebSocket ticket for /ws/inbox?ticket=... and how long it stays valid. */
public record TicketResponse(String ticket, long expiresInSeconds) {
}
