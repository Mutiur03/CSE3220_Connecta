package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelClientFactoryTests {

    private static ChannelClient client(Channel channel) {
        ChannelClient client = mock(ChannelClient.class);
        when(client.channel()).thenReturn(channel);
        return client;
    }

    @Test
    void picksTheClientOfTheChannel() {
        ChannelClient messenger = client(Channel.MESSENGER);
        ChannelClient instagram = client(Channel.INSTAGRAM);
        ChannelClientFactory factory = new ChannelClientFactory(List.of(messenger, instagram));

        assertSame(messenger, factory.forChannel(Channel.MESSENGER));
        assertSame(instagram, factory.forChannel(Channel.INSTAGRAM));
    }

    @Test
    void aChannelWithoutAClientIsNotConnected() {
        ChannelClientFactory factory = new ChannelClientFactory(List.of(client(Channel.MESSENGER)));

        assertThrows(ChannelNotConnectedException.class, () -> factory.forChannel(Channel.INSTAGRAM));
    }
}
