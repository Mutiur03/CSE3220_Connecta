package com.connecta.backend.auth.dto;

import jakarta.servlet.http.HttpServletRequest;

/** Where a session was opened from: the browser's User-Agent and the caller's IP. Both may be unknown. */
public record ClientInfo(String userAgent, String ip) {

    public static final ClientInfo UNKNOWN = new ClientInfo(null, null);

    static final int MAX_USER_AGENT = 255;

    public static ClientInfo from(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        if (agent != null && agent.length() > MAX_USER_AGENT) {
            agent = agent.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(agent, request.getRemoteAddr());
    }
}
