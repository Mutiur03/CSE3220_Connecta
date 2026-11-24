package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.meta.domain.WebhookObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Adapter: reads a Facebook Page's Messenger conversations with its Page token. */
@Component
class MessengerHistoryClient extends GraphHistoryClient {

    private static final String GRAPH_HOST = "https://graph.facebook.com/";

    @Autowired
    MessengerHistoryClient(MetaProperties meta, HistoryImportProperties limits) {
        this(meta, limits, timedBuilder(limits));
    }

    /** The builder is a parameter so tests can bind a mock server to it. */
    MessengerHistoryClient(MetaProperties meta, HistoryImportProperties limits, RestClient.Builder restClients) {
        super(restClients, GRAPH_HOST, meta.graphVersion(), limits);
    }

    @Override
    public Channel channel() {
        return Channel.MESSENGER;
    }

    @Override
    protected WebhookObject object() {
        return WebhookObject.PAGE;
    }

    @Override
    protected String platform() {
        return "messenger";
    }
}
