package com.connecta.backend.meta.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.ProfileLookupProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.service.HistoryImportedEvent;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import com.connecta.backend.inbox.service.ParticipantProfileService;
import com.connecta.backend.inbox.service.ProfileTarget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerProfileListenerTests {

    private static final String PIC = "https://cdn.example/p.jpg";

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final MessageReceivedEvent event = new MessageReceivedEvent(orgId, conversationId, UUID.randomUUID(),
            Channel.MESSENGER, MessageDirection.INBOUND, true);
    private final ProfileTarget customer = new ProfileTarget(orgId, conversationId, Channel.MESSENGER,
            "psid-1", null, null);

    @Mock
    ParticipantProfileService profiles;

    @Mock
    CustomerProfileClientFactory clients;

    @Mock
    CustomerProfileClient client;

    private CustomerProfileListener listener() {
        return new CustomerProfileListener(profiles, clients,
                new ProfileLookupProperties(null, null, null, null, null, 2));
    }

    @Nested
    class OnMessageReceived {

        @Test
        void storesNameAndPictureMetaReturns() {
            when(profiles.findNeedingRefresh(orgId, conversationId)).thenReturn(Optional.of(customer));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(client.fetchProfile(customer)).thenReturn(Optional.of(new CustomerProfile("Rina Das", PIC)));

            listener().onMessageReceived(event);

            verify(profiles).storeProfile(customer, "Rina Das", PIC);
        }

        @Test
        void doesNothingWhenTheProfileIsFresh() {
            when(profiles.findNeedingRefresh(orgId, conversationId)).thenReturn(Optional.empty());

            listener().onMessageReceived(event);

            verifyNoInteractions(clients);
            verify(profiles, never()).storeProfile(any(), any(), any());
        }

        @Test
        void aFailedLookupStoresNothingSoOldValuesStay() {
            when(profiles.findNeedingRefresh(orgId, conversationId)).thenReturn(Optional.of(customer));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(client.fetchProfile(customer)).thenReturn(Optional.empty());

            listener().onMessageReceived(event);

            verify(profiles, never()).storeProfile(any(), any(), any());
        }

        @Test
        void swallowsAConnectionFailure() {
            when(profiles.findNeedingRefresh(orgId, conversationId)).thenReturn(Optional.of(customer));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(client.fetchProfile(customer)).thenThrow(new ChannelNotConnectedException());

            assertThatCode(() -> listener().onMessageReceived(event)).doesNotThrowAnyException();
            verify(profiles, never()).storeProfile(any(), any(), any());
        }

        @Test
        void swallowsADatabaseFailure() {
            when(profiles.findNeedingRefresh(orgId, conversationId)).thenThrow(new IllegalStateException("db down"));

            assertThatCode(() -> listener().onMessageReceived(event)).doesNotThrowAnyException();
        }
    }

    @Nested
    class OnHistoryImported {

        private final HistoryImportedEvent imported = new HistoryImportedEvent(orgId);

        @Test
        void asksForNoMoreCustomersThanTheConfiguredLimit() {
            when(profiles.findNeverLookedUp(orgId, 2)).thenReturn(List.of(customer, customer));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(client.fetchProfile(customer)).thenReturn(Optional.of(new CustomerProfile("Rina", PIC)));

            listener().onHistoryImported(imported);

            verify(profiles).findNeverLookedUp(orgId, 2);
            verify(profiles, times(2)).storeProfile(customer, "Rina", PIC);
        }

        @Test
        void oneFailingCustomerDoesNotStopTheRest() {
            ProfileTarget other = new ProfileTarget(orgId, UUID.randomUUID(), Channel.INSTAGRAM, "ig-1", null, null);
            CustomerProfileClient instagram = mock(CustomerProfileClient.class);
            when(profiles.findNeverLookedUp(orgId, 2)).thenReturn(List.of(customer, other));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(clients.forChannel(Channel.INSTAGRAM)).thenReturn(instagram);
            when(client.fetchProfile(customer)).thenThrow(new ChannelNotConnectedException());
            when(instagram.fetchProfile(other)).thenReturn(Optional.of(new CustomerProfile(null, PIC)));

            listener().onHistoryImported(imported);

            verify(profiles).storeProfile(other, null, PIC);
        }

        @Test
        void swallowsADatabaseFailure() {
            when(profiles.findNeverLookedUp(orgId, 2)).thenThrow(new IllegalStateException("db down"));

            assertThatCode(() -> listener().onHistoryImported(imported)).doesNotThrowAnyException();
        }
    }

    @Nested
    class OnApplicationReady {

        @Test
        void refreshesAtMostTheConfiguredLimitAcrossOrganizations() {
            ProfileTarget other = new ProfileTarget(UUID.randomUUID(), UUID.randomUUID(), Channel.MESSENGER,
                    "psid-2", null, null);
            when(profiles.findNeverLookedUp(2)).thenReturn(List.of(customer, other));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(client.fetchProfile(any())).thenReturn(Optional.of(new CustomerProfile("Rina", PIC)));

            listener().onApplicationReady();

            verify(profiles).findNeverLookedUp(2);
            verify(profiles).storeProfile(customer, "Rina", PIC);
            verify(profiles).storeProfile(other, "Rina", PIC);
        }

        @Test
        void oneFailingCustomerDoesNotStopTheRest() {
            ProfileTarget other = new ProfileTarget(orgId, UUID.randomUUID(), Channel.INSTAGRAM, "ig-1", null, null);
            CustomerProfileClient instagram = mock(CustomerProfileClient.class);
            when(profiles.findNeverLookedUp(2)).thenReturn(List.of(customer, other));
            when(clients.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(clients.forChannel(Channel.INSTAGRAM)).thenReturn(instagram);
            when(client.fetchProfile(customer)).thenThrow(new ChannelNotConnectedException());
            when(instagram.fetchProfile(other)).thenReturn(Optional.of(new CustomerProfile(null, PIC)));

            listener().onApplicationReady();

            verify(profiles).storeProfile(other, null, PIC);
        }

        @Test
        void aDatabaseFailureNeverFailsStartup() {
            when(profiles.findNeverLookedUp(2)).thenThrow(new IllegalStateException("db down"));

            assertThatCode(() -> listener().onApplicationReady()).doesNotThrowAnyException();
        }
    }
}
