package com.connecta.backend.setup.service;

import com.connecta.backend.auth.dto.RegisterRequest;
import com.connecta.backend.auth.dto.UserResponse;
import com.connecta.backend.auth.service.EmailAlreadyUsedException;
import com.connecta.backend.setup.dto.SetupStatusResponse;
import com.connecta.backend.setup.repository.SystemSetupRepository;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-run setup: creates the system OWNER, once. Open to anyone until the owner exists, then closed for
 * good. The single-row system_setup insert decides the winner when two requests arrive together.
 */
@Service
@RequiredArgsConstructor
public class SetupService {

    private final UserService users;
    private final SystemSetupRepository setup;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SetupStatusResponse status() {
        return new SetupStatusResponse(!users.ownerExists());
    }

    @Transactional
    public UserResponse completeSetup(RegisterRequest request) {
        if (users.ownerExists()) {
            throw new SetupAlreadyCompleteException();
        }
        String email = User.normalizeEmail(request.email());
        if (users.emailExists(email)) {
            throw new EmailAlreadyUsedException();
        }
        try {
            User owner = users.create(Roles.OWNER, email, passwordEncoder.encode(request.password()),
                    request.fullName());
            if (setup.claim(owner.getId(), clock.instant()) == 0) {
                throw new SetupAlreadyCompleteException();
            }
            return UserResponse.from(owner);
        } catch (DataIntegrityViolationException e) {
            // A concurrent duplicate email surfaces here, as a 409, and not at commit.
            throw new EmailAlreadyUsedException();
        }
    }
}
