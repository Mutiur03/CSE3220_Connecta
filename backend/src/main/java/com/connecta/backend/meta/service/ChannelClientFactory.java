package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Factory: picks the {@link ChannelClient} for a channel. A new channel is a new bean, no code here changes. */
@Component
public class ChannelClientFactory {

    private final Map<Channel, ChannelClient> clients = new EnumMap<>(Channel.class);

    public ChannelClientFactory(List<ChannelClient> available) {
        available.forEach(client -> clients.put(client.channel(), client));
    }

    public ChannelClient forChannel(Channel channel) {
        ChannelClient client = clients.get(channel);
        if (client == null) {
            throw new ChannelNotConnectedException();
        }
        return client;
    }
}
