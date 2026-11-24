package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.UUID;

/**
 * Published (Observer) after a Facebook Page or Instagram account is connected or reconnected. The history
 * import reacts to it; the connect request never waits for that.
 *
 * @param accountId the id of the connected Page or Instagram account row
 */
public record ChannelConnectedEvent(Channel channel, UUID accountId) {
}
