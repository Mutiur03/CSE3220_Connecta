package com.connecta.backend.inbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.ProfileLookupProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestUsers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

class ParticipantProfileServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final Duration TTL = Duration.ofHours(12);
    private static final String OLD_PIC = "https://cdn.example/old.jpg";
    private static final String PIC = "https://cdn.example/new.jpg";

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID pageId = UUID.randomUUID();
    private final Organization org = new Organization("Acme", "acme");
    private ConversationRepository conversations;
    private ParticipantRepository participants;
    private ApplicationEventPublisher publisher;
    private ParticipantProfileService service;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        participants = mock(ParticipantRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        ProfileLookupProperties lookup = new ProfileLookupProperties(null, null, null, null, TTL, 3);
        service = new ParticipantProfileService(conversations, participants, publisher, lookup,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TestUsers.set(org, "id", orgId);
        conversation = new Conversation(org, Channel.MESSENGER, "psid-1");
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

    private Participant lookedUpAt(Instant fetchedAt) {
        Participant participant = participant("Rina");
        participant.applyProfile(null, OLD_PIC, fetchedAt);
        return participant;
    }

    private ProfileTarget target() {
        return new ProfileTarget(orgId, conversationId, Channel.MESSENGER, "psid-1", pageId, null);
    }

    @Nested
    class FindNeedingRefresh {

        @Test
        void returnsTheTargetWhenTheCustomerHasNoName() {
            participant(null);

            assertThat(service.findNeedingRefresh(orgId, conversationId)).contains(target());
        }

        @Test
        void aBlankNameCountsAsMissing() {
            participant("  ");

            assertThat(service.findNeedingRefresh(orgId, conversationId)).isPresent();
        }

        @Test
        void returnsTheTargetWhenTheCustomerWasNeverLookedUp() {
            participant("Rina");

            assertThat(service.findNeedingRefresh(orgId, conversationId)).contains(target());
        }

        @Test
        void isEmptyWhenTheProfileIsFresh() {
            lookedUpAt(NOW.minus(TTL).plusSeconds(1));

            assertThat(service.findNeedingRefresh(orgId, conversationId)).isEmpty();
        }

        @Test
        void returnsTheTargetExactlyAtTheTtlBoundary() {
            lookedUpAt(NOW.minus(TTL));

            assertThat(service.findNeedingRefresh(orgId, conversationId)).isPresent();
        }

        @Test
        void returnsTheTargetWhenThePictureIsStale() {
            lookedUpAt(NOW.minus(TTL).minusSeconds(1));

            assertThat(service.findNeedingRefresh(orgId, conversationId)).isPresent();
        }

        @Test
        void isEmptyForAnotherOrganizationsConversation() {
            UUID otherOrg = UUID.randomUUID();
            when(conversations.findByIdAndOrganizationId(conversationId, otherOrg)).thenReturn(Optional.empty());

            assertThat(service.findNeedingRefresh(otherOrg, conversationId)).isEmpty();
            verify(participants, never()).findByOrganizationIdAndChannelAndExternalId(any(), any(), any());
        }

        @Test
        void isEmptyWhenTheCustomerRowIsMissing() {
            when(participants.findByOrganizationIdAndChannelAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThat(service.findNeedingRefresh(orgId, conversationId)).isEmpty();
        }
    }

    @Nested
    class FindNeverLookedUp {

        @Test
        void asksForAtMostTheLimitInsideTheOrganization() {
            when(conversations.findWithUnfetchedCustomer(any(), any())).thenReturn(List.of(conversation));

            assertThat(service.findNeverLookedUp(orgId, 3)).containsExactly(target());

            ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
            verify(conversations).findWithUnfetchedCustomer(any(), page.capture());
            assertThat(page.getValue().getPageSize()).isEqualTo(3);
        }

        @Test
        void aZeroLimitSkipsTheQuery() {
            assertThat(service.findNeverLookedUp(orgId, 0)).isEmpty();

            verifyNoInteractions(conversations);
        }
    }

    @Nested
    class StoreProfile {

        @Test
        void storesNameAndPictureAndAnnouncesIt() {
            Participant participant = participant(null);

            assertThat(service.storeProfile(target(), " Rina Das ", PIC)).isTrue();

            assertThat(participant.getDisplayName()).isEqualTo("Rina Das");
            assertThat(participant.getProfilePictureUrl()).isEqualTo(PIC);
            assertThat(participant.getProfilePictureFetchedAt()).isEqualTo(NOW);
            ArgumentCaptor<ParticipantProfileUpdatedEvent> event = ArgumentCaptor
                    .forClass(ParticipantProfileUpdatedEvent.class);
            verify(publisher).publishEvent(event.capture());
            assertThat(event.getValue()).isEqualTo(new ParticipantProfileUpdatedEvent(orgId, conversationId));
        }

        @Test
        void aNewerPictureReplacesTheOldOneButTheNameIsKept() {
            Participant participant = lookedUpAt(NOW.minus(TTL));

            assertThat(service.storeProfile(target(), "Someone Else", PIC)).isTrue();

            assertThat(participant.getDisplayName()).isEqualTo("Rina");
            assertThat(participant.getProfilePictureUrl()).isEqualTo(PIC);
            assertThat(participant.getProfilePictureFetchedAt()).isEqualTo(NOW);
        }

        @Test
        void theSamePictureAgainOnlyRefreshesTheTimeWithoutAnAnnouncement() {
            Participant participant = lookedUpAt(NOW.minus(TTL));

            assertThat(service.storeProfile(target(), null, OLD_PIC)).isFalse();

            assertThat(participant.getProfilePictureFetchedAt()).isEqualTo(NOW);
            verify(publisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void aMissingPictureKeepsTheOldOne() {
            Participant participant = lookedUpAt(NOW.minus(TTL));

            service.storeProfile(target(), "Rina", null);

            assertThat(participant.getProfilePictureUrl()).isEqualTo(OLD_PIC);
        }

        @Test
        void aNonHttpsPictureIsNotStored() {
            Participant participant = participant(null);

            service.storeProfile(target(), "Rina", "http://cdn.example/x.jpg");

            assertThat(participant.getProfilePictureUrl()).isNull();
        }

        @Test
        void cutsAnOverlongNameToTheColumnLimit() {
            Participant participant = participant(null);

            service.storeProfile(target(), "x".repeat(Participant.DISPLAY_NAME_MAX_LENGTH + 20), null);

            assertThat(participant.getDisplayName()).hasSize(Participant.DISPLAY_NAME_MAX_LENGTH);
        }

        @Test
        void aBlankNameAndNoPictureChangesNothing() {
            participant(null);

            assertThat(service.storeProfile(target(), "  ", null)).isFalse();
            verify(publisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void aMissingCustomerRowStoresNothing() {
            when(participants.findByOrganizationIdAndChannelAndExternalId(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThat(service.storeProfile(target(), "Rina", PIC)).isFalse();
        }
    }
}
