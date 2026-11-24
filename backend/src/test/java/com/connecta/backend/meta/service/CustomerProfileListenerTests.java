package com.connecta.backend.meta.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import com.connecta.backend.inbox.service.NamelessCustomer;
import com.connecta.backend.inbox.service.ParticipantProfileService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerProfileListenerTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final MessageReceivedEvent event = new MessageReceivedEvent(orgId, conversationId, UUID.randomUUID(),
            Channel.MESSENGER, MessageDirection.INBOUND, true);
    private final NamelessCustomer customer = new NamelessCustomer(orgId, conversationId, Channel.MESSENGER,
            "psid-1", null, null);

    @Mock
    ParticipantProfileService profiles;

    @Mock
    CustomerProfileClientFactory clients;

    @Mock
    CustomerProfileClient client;

    private CustomerProfileListener listener() {
        return new CustomerProfileListener(profiles, clients);
    }

    @Nested
    class OnMessageReceived {

        @Test
        void storesTheNameMetaReturnsForANamelessCustomer() {
            when(profiles.findNameless(orgId, conversationId)).thenReturn(Optional.of(customer));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(client.fetchName(customer)).thenReturn(Optional.of("Rina Das"));

            listener().onMessageReceived(event);

            verify(profiles).storeName(customer, "Rina Das");
        }

        @Test
        void doesNothingWhenTheCustomerAlreadyHasAName() {
            when(profiles.findNameless(orgId, conversationId)).thenReturn(Optional.empty());

            listener().onMessageReceived(event);

            verifyNoInteractions(clients);
            verify(profiles, never()).storeName(any(), any());
        }

        @Test
        void storesNothingWhenMetaHasNoName() {
            when(profiles.findNameless(orgId, conversationId)).thenReturn(Optional.of(customer));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(client.fetchName(customer)).thenReturn(Optional.empty());

            listener().onMessageReceived(event);

            verify(profiles, never()).storeName(any(), any());
        }

        @Test
        void swallowsALookupFailure() {
            when(profiles.findNameless(orgId, conversationId)).thenReturn(Optional.of(customer));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(client.fetchName(customer)).thenThrow(new ChannelNotConnectedException());

            assertThatCode(() -> listener().onMessageReceived(event)).doesNotThrowAnyException();
            verify(profiles, never()).storeName(any(), any());
        }

        @Test
        void swallowsADatabaseFailure() {
            when(profiles.findNameless(orgId, conversationId)).thenThrow(new IllegalStateException("db down"));

            assertThatCode(() -> listener().onMessageReceived(event)).doesNotThrowAnyException();
        }
    }
}
