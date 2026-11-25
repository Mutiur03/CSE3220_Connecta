package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationHistoryClientFactoryTests {

    private static ConversationHistoryClient clientFor(Channel channel) {
        ConversationHistoryClient client = mock(ConversationHistoryClient.class);
        when(client.channel()).thenReturn(channel);
        return client;
    }

    @Test
    void picksTheClientOfTheChannel() {
        ConversationHistoryClient messenger = clientFor(Channel.MESSENGER);
        ConversationHistoryClient instagram = clientFor(Channel.INSTAGRAM);
        ConversationHistoryClientFactory factory = new ConversationHistoryClientFactory(List.of(messenger, instagram));

        assertSame(messenger, factory.forChannel(Channel.MESSENGER));
        assertSame(instagram, factory.forChannel(Channel.INSTAGRAM));
    }

    @Test
    void aChannelWithoutAClientIsRefused() {
        ConversationHistoryClientFactory factory = new ConversationHistoryClientFactory(
                List.of(clientFor(Channel.MESSENGER)));

        assertThrows(ChannelNotConnectedException.class, () -> factory.forChannel(Channel.INSTAGRAM));
    }
}
