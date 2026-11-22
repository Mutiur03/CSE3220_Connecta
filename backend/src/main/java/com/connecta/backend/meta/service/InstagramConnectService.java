package com.connecta.backend.meta.service;

import com.connecta.backend.config.InstagramProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.meta.service.InstagramOAuthClient.InstagramAccount;
import com.connecta.backend.meta.service.MetaOAuthClient.MetaUserToken;
import com.connecta.backend.meta.service.OAuthStateSigner.OAuthState;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connects an organization's Instagram professional account through Instagram Business Login. An ADMIN starts the
 * connection and gets the login URL; the callback exchanges the code, reads the account and stores its token
 * encrypted. One row per organization and Instagram account, so reconnecting refreshes it.
 *
 * <p>Tenant rule: every step runs {@link TenantAccessService#requireRole} for the organization. The Meta exchanges
 * run outside a transaction and the save is the only write, so a failed step saves nothing.
 */
@Service
@RequiredArgsConstructor
public class InstagramConnectService {

    /** The public callback path, appended to app.meta.base-url and registered as the Instagram redirect URI. */
    public static final String CALLBACK_PATH = "/api/meta/instagram/callback";

    private final MetaProperties meta;
    private final InstagramProperties instagram;
    private final OAuthStateSigner states;
    private final InstagramOAuthClient client;
    private final TokenCipher cipher;
    private final TenantAccessService tenants;
    private final ActiveCaller callers;
    private final MetaConnectedAccountRepository accounts;
    private final Clock clock;

    /** The Instagram login URL for the organization. The caller must be an ADMIN of it. */
    @Transactional(readOnly = true)
    public String startConnection(String subject, UUID organizationId) {
        User caller = callers.load(subject);
        tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN);
        requireConfigured();
        String state = states.issue(organizationId, caller.getId(), clock.instant());
        return client.authorizationUrl(state, redirectUri());
    }

    /**
     * Completes the connection from Instagram's callback: verifies the state, re-checks ADMIN, exchanges the code,
     * extends the token and stores the account with its encrypted token.
     */
    public MetaConnectedAccount completeConnection(String code, String state) {
        requireConfigured();
        OAuthState verified = states.verify(state, clock.instant());
        User caller = callers.load(verified.userId().toString());
        Membership membership = tenants.requireRole(verified.organizationId(), caller.getId(), Roles.ADMIN);

        MetaUserToken shortLived = client.exchangeCode(code, redirectUri());
        MetaUserToken longLived = client.extendToken(shortLived.accessToken());
        InstagramAccount account = client.fetchAccount(longLived.accessToken());
        return save(membership, caller, account, longLived);
    }

    /** The organization's connected Instagram accounts, one page at a time. The caller must be an ADMIN of it. */
    @Transactional(readOnly = true)
    public Page<MetaConnectedAccount> connections(String subject, UUID organizationId, Pageable pageable) {
        User caller = callers.load(subject);
        tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN);
        return accounts.findByOrganizationIdAndPlatform(organizationId, MetaConnectedAccount.PLATFORM_INSTAGRAM,
                pageable);
    }

    private MetaConnectedAccount save(Membership membership, User grantedBy, InstagramAccount account,
            MetaUserToken token) {
        UUID organizationId = membership.getOrganization().getId();
        MetaConnectedAccount row = accounts.findByOrganizationIdAndPlatformAndExternalAccountId(
                        organizationId, MetaConnectedAccount.PLATFORM_INSTAGRAM, account.userId())
                .orElseGet(() -> new MetaConnectedAccount(membership.getOrganization(),
                        MetaConnectedAccount.PLATFORM_INSTAGRAM, account.userId()));
        row.refresh(account.username(), cipher.encrypt(token.accessToken()), grantedBy, clock.instant());
        return accounts.save(row);
    }

    private void requireConfigured() {
        if (!instagram.configured() || isBlank(meta.appSecret()) || isBlank(meta.baseUrl())) {
            throw new MetaNotConfiguredException();
        }
    }

    private String redirectUri() {
        return meta.baseUrl().replaceAll("/+$", "") + CALLBACK_PATH;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
