package com.connecta.backend.support;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
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
 * Replaces Meta in a Spring test: one recording client per channel behind a primary {@link ChannelClientFactory}.
 * It records every send and whether a database transaction was active during it. Call {@link #reset()} first.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubChannelClients {

    private final List<Channel> sentOn = new ArrayList<>();
    private final List<Boolean> transactionActive = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger();

    @Bean
    @Primary
    ChannelClientFactory stubChannelClientFactory() {
        return new ChannelClientFactory(List.of(client(Channel.INSTAGRAM), client(Channel.MESSENGER)));
    }

    public synchronized void reset() {
        sentOn.clear();
        transactionActive.clear();
    }

    public synchronized int sends() {
        return sentOn.size();
    }

    /** Whether each send ran inside a database transaction, oldest first. */
    public synchronized List<Boolean> transactionDuringSends() {
        return List.copyOf(transactionActive);
    }

    private ChannelClient client(Channel channel) {
        return new ChannelClient() {
            @Override
            public Channel channel() {
                return channel;
            }

            @Override
            public String send(Conversation conversation, String text) {
                synchronized (StubChannelClients.this) {
                    sentOn.add(channel);
                    transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
                }
                return "mid.stub." + counter.incrementAndGet();
            }
        };
    }
}
