package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Factory: picks the {@link ConversationHistoryClient} for a channel. A new channel is a new bean. */
@Component
public class ConversationHistoryClientFactory {

    private final Map<Channel, ConversationHistoryClient> clients = new EnumMap<>(Channel.class);

    public ConversationHistoryClientFactory(List<ConversationHistoryClient> available) {
        available.forEach(client -> clients.put(client.channel(), client));
    }

    public ConversationHistoryClient forChannel(Channel channel) {
        ConversationHistoryClient client = clients.get(channel);
        if (client == null) {
            throw new ChannelNotConnectedException();
        }
        return client;
    }
}
