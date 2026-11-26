package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.org.domain.Membership;
import java.util.Optional;

/**
 * Strategy: chooses who takes a new conversation. A new way of assigning is a new implementation, never a
 * new branch in the caller. Implementations run inside the caller's transaction.
 */
public interface AssignmentStrategy {

    AssignmentMode mode();

    /** The member of the conversation's organization who should take it, empty when nobody can. */
    Optional<Membership> choose(Conversation conversation);
}
