package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.meta.domain.HistoryImportFailure;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short database steps of a history import, kept out of the import itself so no transaction or connection
 * is held while Meta is called: load an account, mark it imported or failed, list what is still pending.
 */
@Service
@RequiredArgsConstructor
class HistoryAccountStore {

    private final MetaConnectedPageRepository pages;
    private final MetaConnectedAccountRepository accounts;

    @Transactional(readOnly = true)
    Optional<HistorySource> find(Channel channel, UUID accountId) {
        return switch (channel) {
            case MESSENGER -> pages.findById(accountId).map(HistoryAccountStore::source);
            case INSTAGRAM -> accounts.findById(accountId).map(HistoryAccountStore::source);
        };
    }

    @Transactional
    void markImported(Channel channel, UUID accountId, Instant at) {
        switch (channel) {
            case MESSENGER -> pages.findById(accountId).ifPresent(page -> page.markHistoryImported(at));
            case INSTAGRAM -> accounts.findById(accountId).ifPresent(account -> account.markHistoryImported(at));
        }
    }

    @Transactional
    void markFailed(Channel channel, UUID accountId, HistoryImportFailure failure, Instant at) {
        switch (channel) {
            case MESSENGER -> pages.findById(accountId)
                    .ifPresent(page -> page.markHistoryImportFailed(failure, at));
            case INSTAGRAM -> accounts.findById(accountId)
                    .ifPresent(account -> account.markHistoryImportFailed(failure, at));
        }
    }

    @Transactional(readOnly = true)
    List<PendingHistoryImport> pending() {
        return Stream.concat(
                pages.findAllByHistoryImportedAtIsNull().stream()
                        .map(page -> new PendingHistoryImport(Channel.MESSENGER, page.getId())),
                accounts.findAllByHistoryImportedAtIsNull().stream()
                        .map(account -> new PendingHistoryImport(Channel.INSTAGRAM, account.getId())))
                .toList();
    }

    private static HistorySource source(MetaConnectedPage page) {
        Hibernate.initialize(page.getOrganization());
        return new HistorySource(page, page.getId(), Channel.MESSENGER, page.getOrganization().getId(),
                page.getPageId(), page.getPageAccessTokenEncrypted());
    }

    private static HistorySource source(MetaConnectedAccount account) {
        Hibernate.initialize(account.getOrganization());
        return new HistorySource(account, account.getId(), Channel.INSTAGRAM, account.getOrganization().getId(),
                account.getExternalAccountId(), account.getAccessTokenEncrypted());
    }
}
