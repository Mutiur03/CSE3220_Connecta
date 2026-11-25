package com.connecta.backend.meta.domain;

/** Why the last history import of a connection failed. A short code only: Meta's text is never stored. */
public enum HistoryImportFailure {
    /** Meta refused to share conversations: a messaging permission is missing or the token no longer works. */
    PERMISSION,
    /** Meta could not be read (network, timeout or an unexpected answer). */
    FETCH
}
