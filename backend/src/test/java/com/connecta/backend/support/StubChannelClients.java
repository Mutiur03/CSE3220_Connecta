package com.connecta.backend.support;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.meta.service.AttachmentChannelClient;
import com.connecta.backend.meta.service.AttachmentChannelClientFactory;
import com.connecta.backend.meta.service.ChannelClient;
import com.connecta.backend.meta.service.ChannelClientFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Replaces Meta in a Spring test: one recording client per channel behind a primary {@link ChannelClientFactory}
 * and {@link AttachmentChannelClientFactory}. It records every send (text or attachment) and whether a database
 * transaction was active during it. Every attachment kind is accepted. Call {@link #reset()} first.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubChannelClients {

    private final List<Channel> sentOn = new ArrayList<>();
    private final List<Boolean> transactionActive = new ArrayList<>();
    private final List<String> attachmentUrls = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final List<RecordingClient> clients = List.of(new RecordingClient(Channel.INSTAGRAM),
            new RecordingClient(Channel.MESSENGER));

    @Bean
    @Primary
    ChannelClientFactory stubChannelClientFactory() {
        return new ChannelClientFactory(List.copyOf(clients));
    }

    @Bean
    @Primary
    AttachmentChannelClientFactory stubAttachmentChannelClientFactory() {
        return new AttachmentChannelClientFactory(List.copyOf(clients));
    }

    public synchronized void reset() {
        sentOn.clear();
        transactionActive.clear();
        attachmentUrls.clear();
    }

    public synchronized int sends() {
        return sentOn.size();
    }

    /** Whether each send ran inside a database transaction, oldest first. */
    public synchronized List<Boolean> transactionDuringSends() {
        return List.copyOf(transactionActive);
    }

    /** The file URL of each attachment sent, oldest first. */
    public synchronized List<String> attachmentUrls() {
        return List.copyOf(attachmentUrls);
    }

    private final class RecordingClient implements ChannelClient, AttachmentChannelClient {

        private final Channel channel;

        RecordingClient(Channel channel) {
            this.channel = channel;
        }

        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public String send(Conversation conversation, String text) {
            return recordSend();
        }

        @Override
        public boolean supports(AttachmentKind kind) {
            return true;
        }

        @Override
        public String sendAttachment(Conversation conversation, AttachmentKind kind, String url) {
            synchronized (StubChannelClients.this) {
                attachmentUrls.add(url);
            }
            return recordSend();
        }

        private String recordSend() {
            synchronized (StubChannelClients.this) {
                sentOn.add(channel);
                transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            }
            return "mid.stub." + counter.incrementAndGet();
        }
    }
}
