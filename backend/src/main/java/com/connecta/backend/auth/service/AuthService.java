package com.connecta.backend.auth.service;

import com.connecta.backend.auth.dto.LoginRequest;
import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.TokenResponse;
import com.connecta.backend.auth.dto.UserResponse;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokens;
    // Checked when the email is unknown, so unknown and wrong-password logins take similar time.
    private final String dummyHash;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            TokenService tokenService, RefreshTokenService refreshTokens) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName());
        Role agent = roles.findByName(Roles.AGENT)
                .orElseThrow(() -> new IllegalStateException("Role AGENT is not seeded"));
        user.addRole(agent);
        try {
            // Flush now so a concurrent duplicate surfaces here, as a 409, and not at commit.
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
        return UserResponse.from(user);
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

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private TokenResponse tokensFor(User user, String refreshToken) {
        return new TokenResponse("Bearer", tokenService.createAccessToken(user),
                tokenService.accessTtlSeconds(), refreshToken, UserResponse.from(user));
    }
}
