package com.connecta.backend.meta.service;

/** A Page the admin's Meta grant includes, and whether this organization already connected it. No token. */
public record MetaAvailablePage(String pageId, String name, boolean connected) {
}
