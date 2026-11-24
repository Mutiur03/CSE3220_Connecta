package com.connecta.backend.auth.service;

import lombok.RequiredArgsConstructor;
import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived signed access tokens. Refresh tokens are handled by RefreshTokenService. */
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_SESSION = "sid";

    private final JwtEncoder encoder;
    private final JwtProperties props;
    private final Clock clock;

    public String createAccessToken(User user, UUID sessionId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_SESSION, sessionId.toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, user.getRoleNames().stream().sorted().toList())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** The session an access token belongs to, or null for a token issued before sessions existed. */
    public static UUID sessionId(Jwt jwt) {
        String sid = jwt.getClaimAsString(CLAIM_SESSION);
        return sid == null ? null : UUID.fromString(sid);
    }

    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }
}
