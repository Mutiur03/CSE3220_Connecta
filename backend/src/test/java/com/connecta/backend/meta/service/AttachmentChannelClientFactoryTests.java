package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttachmentChannelClientFactoryTests {

    private static AttachmentChannelClient client(Channel channel) {
        AttachmentChannelClient client = mock(AttachmentChannelClient.class);
        when(client.channel()).thenReturn(channel);
        return client;
    }

    @Test
    void picksTheClientOfTheChannel() {
        AttachmentChannelClient messenger = client(Channel.MESSENGER);
        AttachmentChannelClient instagram = client(Channel.INSTAGRAM);
        AttachmentChannelClientFactory factory = new AttachmentChannelClientFactory(List.of(messenger, instagram));

        assertSame(messenger, factory.forChannel(Channel.MESSENGER));
        assertSame(instagram, factory.forChannel(Channel.INSTAGRAM));
    }

    @Test
    void aChannelWithoutAClientIsNotConnected() {
        AttachmentChannelClientFactory factory =
                new AttachmentChannelClientFactory(List.of(client(Channel.MESSENGER)));

        assertThrows(ChannelNotConnectedException.class, () -> factory.forChannel(Channel.INSTAGRAM));
    }
}
