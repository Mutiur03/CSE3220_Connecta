package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.meta.domain.WebhookObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Adapter: reads an Instagram account's conversations with its account token. */
@Component
class InstagramHistoryClient extends GraphHistoryClient {

    private static final String GRAPH_HOST = "https://graph.instagram.com/";

    @Autowired
    InstagramHistoryClient(MetaProperties meta, HistoryImportProperties limits) {
        this(meta, limits, timedBuilder(limits));
    }

    /** The builder is a parameter so tests can bind a mock server to it. */
    InstagramHistoryClient(MetaProperties meta, HistoryImportProperties limits, RestClient.Builder restClients) {
        super(restClients, GRAPH_HOST, meta.graphVersion(), limits);
    }

    @Override
    public Channel channel() {
        return Channel.INSTAGRAM;
    }

    @Override
    protected WebhookObject object() {
        return WebhookObject.INSTAGRAM;
    }

    @Override
    protected String platform() {
        return "instagram";
    }
}
