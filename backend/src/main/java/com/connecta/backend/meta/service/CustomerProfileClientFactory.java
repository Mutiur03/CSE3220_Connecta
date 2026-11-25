package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Factory: picks the {@link CustomerProfileClient} for a channel. A new channel is a new bean. */
@Component
public class CustomerProfileClientFactory {

    private final Map<Channel, CustomerProfileClient> clients = new EnumMap<>(Channel.class);

    public CustomerProfileClientFactory(List<CustomerProfileClient> available) {
        available.forEach(client -> clients.put(client.channel(), client));
    }

    public CustomerProfileClient forChannel(Channel channel) {
        CustomerProfileClient client = clients.get(channel);
        if (client == null) {
            throw new ChannelNotConnectedException();
        }
        return client;
    }
}
