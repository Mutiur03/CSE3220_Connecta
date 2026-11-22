package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.MetaAuthorization;
import com.connecta.backend.meta.repository.MetaAuthorizationRepository;
import com.connecta.backend.meta.service.MetaOAuthClient.MetaUserToken;
import com.connecta.backend.meta.service.OAuthStateSigner.OAuthState;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connects an organization to Meta. An ADMIN of the organization starts the connection and gets the Meta login
 * URL. Meta then calls back with a code; the state names the organization and admin, and the role is checked
 * again at the callback. The exchanged token is stored encrypted, one grant per organization.
 *
 * <p>Tenant rule: every step runs {@link TenantAccessService#requireRole} for the organization, so a non-member
 * gets 404 and a lower role 403. The Meta exchange runs outside a transaction; only the save is transactional.
 */
@Service
@RequiredArgsConstructor
public class MetaAuthorizationService {

    /** The public callback path, appended to app.meta.base-url and registered as the Meta redirect URI. */
    public static final String CALLBACK_PATH = "/api/meta/oauth/callback";

    private final MetaProperties properties;
    private final OAuthStateSigner states;
    private final MetaOAuthClient meta;
    private final TokenCipher cipher;
    private final TenantAccessService tenants;
    private final ActiveCaller callers;
    private final MetaAuthorizationRepository authorizations;
    private final Clock clock;

    /** The Meta login URL for the organization. The caller must be an ADMIN of it. */
    @Transactional(readOnly = true)
    public String startAuthorization(String subject, UUID organizationId) {
        User caller = callers.load(subject);
        tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN);
        requireConfigured();
        String state = states.issue(organizationId, caller.getId(), clock.instant());
        return meta.authorizationUrl(state, redirectUri());
    }

    /**
     * Completes the connection from the Meta callback: verifies the state, re-checks that the admin still holds
     * ADMIN, exchanges the code for a long-lived token and stores it encrypted.
     */
    public MetaAuthorization completeAuthorization(String code, String state) {
        requireConfigured();
        OAuthState verified = states.verify(state, clock.instant());
        User caller = callers.load(verified.userId().toString());
        Membership membership = tenants.requireRole(verified.organizationId(), caller.getId(), Roles.ADMIN);

        if (code == null || code.isBlank()) {
            throw new MetaAuthorizationDeniedException();
        }
        MetaUserToken shortLived = meta.exchangeCode(code, redirectUri());
        MetaUserToken longLived = meta.extendToken(shortLived.accessToken());
        return save(membership, caller, longLived);
    }

    /** The organization's grant, or empty when it has never connected. The caller must be an ADMIN of it. */
    @Transactional(readOnly = true)
    public Optional<MetaAuthorization> authorization(String subject, UUID organizationId) {
        User caller = callers.load(subject);
        tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN);
        return authorizations.findByOrganizationId(organizationId);
    }

    private MetaAuthorization save(Membership membership, User grantedBy, MetaUserToken token) {
        MetaAuthorization grant = authorizations.findByOrganizationId(membership.getOrganization().getId())
                .orElseGet(() -> new MetaAuthorization(membership.getOrganization()));
        grant.replaceToken(cipher.encrypt(token.accessToken()), grantedBy, token.expiresAt(), clock.instant());
        return authorizations.save(grant);
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new MetaNotConfiguredException();
        }
    }

    private String redirectUri() {
        return properties.baseUrl().replaceAll("/+$", "") + CALLBACK_PATH;
    }
}
