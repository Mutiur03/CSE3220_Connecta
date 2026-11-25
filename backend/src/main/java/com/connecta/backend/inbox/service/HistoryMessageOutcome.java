package com.connecta.backend.inbox.service;

/** What storing one message read from Meta's history did to the organization's inbox. */
public enum HistoryMessageOutcome {
    /** The organization did not have the message; it was stored. */
    INSERTED,
    /** The organization had it; its body was corrected or missing attachments were added. */
    UPDATED,
    /** The organization had it exactly as Meta returns it; nothing was written. */
    UNCHANGED
}
