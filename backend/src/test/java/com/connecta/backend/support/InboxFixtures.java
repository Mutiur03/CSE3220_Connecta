package com.connecta.backend.support;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;

/** Saves users, organizations and conversations through the real repositories of a database-backed test. */
public final class InboxFixtures {

    private final UserRepository users;
    private final RoleRepository roles;
    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final ParticipantRepository participants;
    private final ConversationRepository conversations;
    private final ConversationParticipantRepository links;
    private final MessageRepository messages;

    public InboxFixtures(UserRepository users, RoleRepository roles, OrganizationRepository organizations,
            MembershipRepository memberships, ParticipantRepository participants,
            ConversationRepository conversations, ConversationParticipantRepository links,
            MessageRepository messages) {
        this.users = users;
        this.roles = roles;
        this.organizations = organizations;
        this.memberships = memberships;
        this.participants = participants;
        this.conversations = conversations;
        this.links = links;
        this.messages = messages;
    }

    public User user(String roleName) {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(roles.findByName(roleName).orElseThrow());
        return users.saveAndFlush(user);
    }

    public Organization organization(String name) {
        return organizations.saveAndFlush(new Organization(name, "org-" + UUID.randomUUID()));
    }

    public void join(Organization organization, User user, String roleName) {
        memberships.saveAndFlush(new Membership(organization, user, roles.findByName(roleName).orElseThrow()));
    }

    /** A conversation with one customer and one inbound message. */
    public Conversation conversation(Organization organization, Channel channel, String customer, String text,
            Instant at) {
        Participant participant = participants.saveAndFlush(
                new Participant(organization, channel, "ext-" + UUID.randomUUID(), customer));
        Conversation conversation = new Conversation(organization, channel, "thread-" + UUID.randomUUID());
        conversation.recordMessageAt(at);
        conversation = conversations.saveAndFlush(conversation);
        links.saveAndFlush(new ConversationParticipant(conversation, participant));
        messages.saveAndFlush(Message.inbound(conversation, participant, "m-" + UUID.randomUUID(), text, at));
        return conversation;
    }
}
