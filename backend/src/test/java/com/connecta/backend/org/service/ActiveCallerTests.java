package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActiveCallerTests {

    @Mock
    UserService users;

    @Nested
    class Load {

        @Test
        void returnsTheEnabledAccountForTheSubject() {
            ActiveCaller caller = new ActiveCaller(users);
            User jane = TestUsers.user("jane@example.com", Roles.AGENT);
            when(users.findEnabledById(jane.getId())).thenReturn(Optional.of(jane));

            assertSame(jane, caller.load(jane.getId().toString()));
        }

        @Test
        void aDisabledOrUnknownAccountIsNotAvailable() {
            ActiveCaller caller = new ActiveCaller(users);
            UUID id = UUID.randomUUID();
            when(users.findEnabledById(id)).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class, () -> caller.load(id.toString()));
        }

        @Test
        void aSubjectThatIsNotAUuidIsNotAvailable() {
            ActiveCaller caller = new ActiveCaller(users);

            assertThrows(AccountNotAvailableException.class, () -> caller.load("not-a-uuid"));
        }

        @Test
        void aMissingSubjectIsNotAvailable() {
            ActiveCaller caller = new ActiveCaller(users);

            assertThrows(AccountNotAvailableException.class, () -> caller.load(null));
        }
    }
}
