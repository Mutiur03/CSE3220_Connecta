package com.connecta.backend.meta.service;

/** What Meta knows about a customer: the display name and the (signed, expiring) picture URL. Either may be null. */
public record CustomerProfile(String name, String pictureUrl) {
}
