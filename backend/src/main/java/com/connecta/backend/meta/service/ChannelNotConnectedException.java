package com.connecta.backend.meta.service;

/** The organization has no connected Meta account for the conversation's channel. */
public class ChannelNotConnectedException extends RuntimeException {

    public ChannelNotConnectedException() {
        super("No connected account for this channel. Connect it on the Integrations page.");
    }
}
