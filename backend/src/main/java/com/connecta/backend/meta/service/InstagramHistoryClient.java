package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.meta.domain.WebhookObject;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Adapter: reads an Instagram account's conversations with its account token. A shared post or reel arrives
 * with neither text nor attachments, so such a message is looked up on its own ({@code shares}): asking for
 * that field inside the conversation list makes Graph hang for minutes.
 */
@Component
class InstagramHistoryClient extends GraphHistoryClient {

    private static final String GRAPH_HOST = "https://graph.instagram.com/";
    private static final String SHARES = "shares";

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

    /** The posts and reels the message shares, each a LINK titled "Shared post"; non-http(s) links are dropped. */
    @Override
    protected List<NormalizedAttachment> linksOf(String messageId, GraphReader graph) {
        List<NormalizedAttachment> links = new ArrayList<>();
        for (JsonNode share : graph.get("/{id}?fields={fields}", messageId, SHARES).path(SHARES).path("data")) {
            NormalizedAttachment.link(share.path("link").asText(""), NormalizedAttachment.SHARED_POST)
                    .ifPresent(links::add);
        }
        return links;
    }
}
