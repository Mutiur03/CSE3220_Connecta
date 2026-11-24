package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerProfileClientFactoryTests {

    private static CustomerProfileClient client(Channel channel) {
        CustomerProfileClient client = mock(CustomerProfileClient.class);
        when(client.channel()).thenReturn(channel);
        return client;
    }

    @Test
    void picksTheClientOfTheChannel() {
        CustomerProfileClient messenger = client(Channel.MESSENGER);
        CustomerProfileClient instagram = client(Channel.INSTAGRAM);
        CustomerProfileClientFactory factory = new CustomerProfileClientFactory(List.of(messenger, instagram));

        assertSame(messenger, factory.forChannel(Channel.MESSENGER));
        assertSame(instagram, factory.forChannel(Channel.INSTAGRAM));
    }

    @Test
    void aChannelWithoutAClientIsNotConnected() {
        CustomerProfileClientFactory factory = new CustomerProfileClientFactory(List.of(client(Channel.MESSENGER)));

        assertThrows(ChannelNotConnectedException.class, () -> factory.forChannel(Channel.INSTAGRAM));
    }
}
