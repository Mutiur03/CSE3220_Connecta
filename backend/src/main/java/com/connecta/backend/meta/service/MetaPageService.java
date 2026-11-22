package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.MetaAuthorization;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.repository.MetaAuthorizationRepository;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.meta.service.MetaOAuthClient.MetaPage;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connects an organization's Facebook Pages for Messenger, using the Meta grant stored by
 * {@link MetaAuthorizationService}. An ADMIN picks Pages from the ones the grant includes; each is subscribed to
 * messages and message echoes, then stored with its Page token encrypted.
 *
 * <p>Tenant rule: every call runs {@link TenantAccessService#requireRole} for ADMIN. Nothing is saved until every
 * Page is subscribed, so a Meta failure leaves no half-connected Pages in the database.
 */
@Service
@RequiredArgsConstructor
public class MetaPageService {

    private static final Sort BY_NAME = Sort.by("pageName").ascending().and(Sort.by("id"));

    private final MetaProperties properties;
    private final MetaAuthorizationRepository authorizations;
    private final MetaConnectedPageRepository pages;
    private final MetaOAuthClient meta;
    private final TokenCipher cipher;
    private final TenantAccessService tenants;
    private final ActiveCaller callers;
    private final Clock clock;

    /** The organization's connected Pages, by name. Never includes a token. */
    @Transactional(readOnly = true)
    public Page<MetaConnectedPage> connectedPages(String subject, UUID organizationId, int page, int size) {
        User caller = callers.load(subject);
        tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN);
        return pages.findByOrganizationId(organizationId, PageRequest.of(page, size, BY_NAME));
    }

    /**
     * The Pages the stored grant includes, by name, each marked connected or not. Without a grant there are none,
     * so the connect screen can ask the admin to connect Facebook first. Never includes a token.
     */
    @Transactional(readOnly = true)
    public List<MetaAvailablePage> availablePages(String subject, UUID organizationId) {
        User caller = callers.load(subject);
        tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN);
        requireConfigured();
        if (authorizations.findByOrganizationId(organizationId).isEmpty()) {
            return List.of();
        }
        Set<String> connected = pages.findByOrganizationId(organizationId, Pageable.unpaged()).stream()
                .map(MetaConnectedPage::getPageId)
                .collect(Collectors.toSet());
        return grantedPages(organizationId).values().stream()
                .map(page -> new MetaAvailablePage(page.id(), page.name(), connected.contains(page.id())))
                .sorted(Comparator.comparing(MetaAvailablePage::name).thenComparing(MetaAvailablePage::pageId))
                .toList();
    }

    /**
     * Connects the chosen Pages. Every id must be in the stored grant, or the whole request is refused with
     * {@link MetaPageNotAllowedException}. Reconnecting a Page replaces its stored token.
     */
    public List<MetaConnectedPage> connect(String subject, UUID organizationId, Collection<String> pageIds) {
        User caller = callers.load(subject);
        Membership membership = tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN);
        requireConfigured();

        Set<String> wanted = new LinkedHashSet<>(pageIds);
        Map<String, MetaPage> granted = grantedPages(organizationId);
        if (!granted.keySet().containsAll(wanted)) {
            throw new MetaPageNotAllowedException();
        }
        for (String pageId : wanted) {
            meta.subscribePage(pageId, granted.get(pageId).accessToken());
        }
        Instant now = clock.instant();
        return wanted.stream()
                .map(pageId -> save(membership.getOrganization(), caller, granted.get(pageId), now))
                .toList();
    }

    /** The Pages the stored grant includes, keyed by Page id. A missing grant includes none. */
    private Map<String, MetaPage> grantedPages(UUID organizationId) {
        MetaAuthorization grant = authorizations.findByOrganizationId(organizationId)
                .orElseThrow(MetaPageNotAllowedException::new);
        String userToken = cipher.decrypt(grant.getAccessTokenEncrypted());
        return meta.listPages(userToken).stream()
                .collect(Collectors.toMap(MetaPage::id, Function.identity()));
    }

    private MetaConnectedPage save(Organization organization, User grantedBy, MetaPage page, Instant now) {
        MetaConnectedPage connected = pages.findByOrganizationIdAndPageId(organization.getId(), page.id())
                .orElseGet(() -> new MetaConnectedPage(organization, page.id()));
        connected.replace(page.name(), cipher.encrypt(page.accessToken()), grantedBy, now);
        return pages.save(connected);
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new MetaNotConfiguredException();
        }
    }
}
