package com.connecta.backend.meta.service;

import java.util.List;

/**
 * One past conversation read from Meta, in the common internal format.
 *
 * @param peerId the customer's Meta id
 * @param peerName the customer's name or username, or null when Meta gave none
 * @param messages the messages, oldest first
 * @param moreMessages true when Meta has older messages than the ones read
 */
public record HistoryConversation(String peerId, String peerName, List<NormalizedMessage> messages,
        boolean moreMessages) {
}
