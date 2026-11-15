package com.connecta.backend.org.service;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns the JWT subject into the caller's account, only while the account exists and is enabled. Every
 * organization service starts here, so a disabled user with an unexpired token is refused everywhere.
 */
@Component
@RequiredArgsConstructor
public class ActiveCaller {

    private final UserService users;

    /** The enabled account for the subject. A missing, malformed or disabled account is AccountNotAvailableException. */
    public User load(String subject) {
        return users.findEnabledById(parseUserId(subject)).orElseThrow(AccountNotAvailableException::new);
    }

    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AccountNotAvailableException();
        }
    }
}
