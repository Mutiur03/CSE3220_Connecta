package com.connecta.backend.support;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Gives each test run its own signing secret, so no secret is committed to the repository. */
public class RandomJwtSecret implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String SECRET = randomHex();

    private static String randomHex() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of("app.jwt.secret=" + SECRET).applyTo(context);
    }
}
