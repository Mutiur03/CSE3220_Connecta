package com.connecta.backend.auth.service;

import com.connecta.backend.auth.dto.LoginRequest;
import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.TokenResponse;
import com.connecta.backend.auth.dto.UserResponse;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokens;
    // Checked when the email is unknown, so unknown and wrong-password logins take similar time.
    // Hashed with the injected encoder, so it always has the same cost as real hashes.
    private final String dummyHash;

    public AuthService(UserService users, PasswordEncoder passwordEncoder,
            TokenService tokenService, RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** Signup makes an ADMIN. Agents come only from invitations. Closed until the first-run setup is done. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (!users.ownerExists()) {
            throw new SetupRequiredException();
        }
        String email = User.normalizeEmail(request.email());
        if (users.emailExists(email)) {
            throw new EmailAlreadyUsedException();
        }
        try {
            User user = users.create(Roles.ADMIN, email, passwordEncoder.encode(request.password()), request.fullName());
            return UserResponse.from(user);
        } catch (DataIntegrityViolationException e) {
            // A concurrent duplicate surfaces here, as a 409, and not at commit.
            throw new EmailAlreadyUsedException();
        }
    }

    // Not readOnly: login stores a refresh token, and Postgres rejects writes in a read-only transaction.
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokensFor(user, refreshTokens.issue(user));
    }

    // noRollbackFor: reuse detection revokes sessions and that must be kept when the request is rejected.
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenService.Issued issued = refreshTokens.rotate(refreshToken);
        return tokensFor(issued.user(), issued.rawToken());
    }

    /** The signed-in user. A valid token is not enough: deleted or disabled accounts are rejected. */
    @Transactional(readOnly = true)
    public UserResponse currentUser(String subject) {
        return users.findEnabledById(parseUserId(subject))
                .map(UserResponse::from)
                .orElseThrow(AccountNotAvailableException::new);
    }

    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AccountNotAvailableException();
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    /** Signs a user in without a password check, for flows that already proved who they are (an invitation token). */
    @Transactional
    public TokenResponse startSession(User user) {
        return tokensFor(user, refreshTokens.issue(user));
    }

    private TokenResponse tokensFor(User user, String refreshToken) {
        return new TokenResponse(TokenResponse.BEARER, tokenService.createAccessToken(user),
                tokenService.accessTtlSeconds(), refreshToken, UserResponse.from(user));
    }
}
