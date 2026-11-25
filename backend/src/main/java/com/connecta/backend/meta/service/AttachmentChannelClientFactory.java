package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Factory: picks the {@link AttachmentChannelClient} for a channel. A new channel is a new bean, no code here changes. */
@Component
public class AttachmentChannelClientFactory {

    private final Map<Channel, AttachmentChannelClient> clients = new EnumMap<>(Channel.class);

    public AttachmentChannelClientFactory(List<AttachmentChannelClient> available) {
        available.forEach(client -> clients.put(client.channel(), client));
    }

    public AttachmentChannelClient forChannel(Channel channel) {
        AttachmentChannelClient client = clients.get(channel);
        if (client == null) {
            throw new ChannelNotConnectedException();
        }
        return client;
    }
}
