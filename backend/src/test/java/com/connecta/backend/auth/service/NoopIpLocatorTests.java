package com.connecta.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoopIpLocatorTests {

    @Test
    void neverKnowsAPlace() {
        assertTrue(new NoopIpLocator().locate("203.0.113.7").isEmpty());
    }
}
