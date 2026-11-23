package com.connecta.backend.auth.service;

import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changes a user's own password and signs their other devices out. */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserService users;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;

    /**
     * @param currentSessionId the session making the request, kept signed in. Null for an access token
     *     issued before sessions existed: the device is then unknown, so every session is revoked.
     */
    @Transactional
    public void change(UUID userId, UUID currentSessionId, String currentPassword, String newPassword) {
        User user = users.findEnabledById(userId).orElseThrow(AccountNotAvailableException::new);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        if (currentPassword.equals(newPassword)) {
            throw new PasswordUnchangedException();
        }
        users.changePasswordHash(user, passwordEncoder.encode(newPassword));
        if (currentSessionId == null) {
            sessions.revokeAll(userId);
        } else {
            sessions.revokeOthers(userId, currentSessionId);
        }
    }
}
