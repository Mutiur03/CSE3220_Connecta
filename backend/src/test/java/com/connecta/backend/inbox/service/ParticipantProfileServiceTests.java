package com.connecta.backend.inbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestUsers;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ParticipantProfileServiceTests {

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID pageId = UUID.randomUUID();
    private final Organization org = new Organization("Acme", "acme");
    private ConversationRepository conversations;
    private ParticipantRepository participants;
    private ApplicationEventPublisher publisher;
    private ParticipantProfileService service;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        participants = mock(ParticipantRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new ParticipantProfileService(conversations, participants, publisher);
        TestUsers.set(org, "id", orgId);
        Conversation conversation = new Conversation(org, Channel.MESSENGER, "psid-1");
        TestUsers.set(conversation, "id", conversationId);
        MetaConnectedPage page = new MetaConnectedPage(org, "page-1");
        TestUsers.set(page, "id", pageId);
        conversation.linkToPage(page);
        when(conversations.findByIdAndOrganizationId(conversationId, orgId)).thenReturn(Optional.of(conversation));
    }

    private Participant participant(String name) {
        Participant participant = new Participant(org, Channel.MESSENGER, "psid-1", name);
        when(participants.findByOrganizationIdAndChannelAndExternalId(orgId, Channel.MESSENGER, "psid-1"))
                .thenReturn(Optional.of(participant));
        return participant;
    }

    @Nested
    class FindNameless {

        @Test
        void returnsTheLookupTargetWhenTheCustomerHasNoName() {
            participant(null);

            assertThat(service.findNameless(orgId, conversationId)).contains(
                    new NamelessCustomer(orgId, conversationId, Channel.MESSENGER, "psid-1", pageId, null));
        }

        @Test
        void aBlankNameCountsAsMissing() {
            participant("  ");

            assertThat(service.findNameless(orgId, conversationId)).isPresent();
        }

        @Test
        void isEmptyWhenTheCustomerAlreadyHasAName() {
            participant("Rina");

            assertThat(service.findNameless(orgId, conversationId)).isEmpty();
        }

        @Test
        void isEmptyForAnotherOrganizationsConversation() {
            UUID otherOrg = UUID.randomUUID();
            when(conversations.findByIdAndOrganizationId(conversationId, otherOrg)).thenReturn(Optional.empty());

            assertThat(service.findNameless(otherOrg, conversationId)).isEmpty();
            verify(participants, never()).findByOrganizationIdAndChannelAndExternalId(any(), any(), any());
        }

        @Test
        void isEmptyWhenTheCustomerRowIsMissing() {
            when(participants.findByOrganizationIdAndChannelAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThat(service.findNameless(orgId, conversationId)).isEmpty();
        }
    }

    @Nested
    class StoreName {

        private final NamelessCustomer customer = new NamelessCustomer(orgId, conversationId, Channel.MESSENGER,
                "psid-1", pageId, null);

        @Test
        void storesTheNameAndAnnouncesIt() {
            Participant participant = participant(null);

            assertThat(service.storeName(customer, " Rina Das ")).isTrue();

            assertThat(participant.getDisplayName()).isEqualTo("Rina Das");
            ArgumentCaptor<ParticipantProfileUpdatedEvent> event = ArgumentCaptor
                    .forClass(ParticipantProfileUpdatedEvent.class);
            verify(publisher).publishEvent(event.capture());
            assertThat(event.getValue()).isEqualTo(new ParticipantProfileUpdatedEvent(orgId, conversationId));
        }

        @Test
        void neverOverwritesAnExistingName() {
            Participant participant = participant("Rina");

            assertThat(service.storeName(customer, "Someone Else")).isFalse();

            assertThat(participant.getDisplayName()).isEqualTo("Rina");
            verify(publisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void cutsAnOverlongNameToTheColumnLimit() {
            Participant participant = participant(null);

            service.storeName(customer, "x".repeat(Participant.DISPLAY_NAME_MAX_LENGTH + 20));

            assertThat(participant.getDisplayName()).hasSize(Participant.DISPLAY_NAME_MAX_LENGTH);
        }

        @Test
        void aBlankNameIsNotStored() {
            participant(null);

            assertThat(service.storeName(customer, "  ")).isFalse();
            verify(publisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void aMissingCustomerRowStoresNothing() {
            when(participants.findByOrganizationIdAndChannelAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThat(service.storeName(customer, "Rina")).isFalse();
        }
    }
}
