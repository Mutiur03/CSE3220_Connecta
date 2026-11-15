package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.dto.CreateOrganizationRequest;
import com.connecta.backend.org.dto.OrganizationResponse;
import com.connecta.backend.org.dto.UpdateOrganizationRequest;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.RoleNotSeededException;
import com.connecta.backend.user.service.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTests {

    private static final String NAME = "Acme Support";
    private static final String SLUG = "acme-support";

    @Mock
    OrganizationRepository organizations;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    @InjectMocks
    OrganizationService service;

    private final User caller = TestUsers.user("jane@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();
    private final UUID organizationId = UUID.randomUUID();

    private Organization organization() {
        Organization organization = new Organization(NAME, SLUG);
        TestUsers.set(organization, "id", organizationId);
        return organization;
    }

    /** The caller's membership of the organization, with the given role. */
    private Membership membershipAs(String roleName) {
        return new Membership(organization(), caller, new Role(roleName));
    }

    private static CreateOrganizationRequest createRequest() {
        return new CreateOrganizationRequest(NAME, SLUG);
    }

    @Nested
    class Create {

        @Test
        void savesTheOrganizationAndMakesTheCallerOwner() {
            Role owner = new Role(Roles.OWNER);
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
            when(users.findRole(Roles.OWNER)).thenReturn(owner);
            when(organizations.existsBySlug(SLUG)).thenReturn(false);
            when(organizations.saveAndFlush(any(Organization.class))).thenAnswer(call -> call.getArgument(0));

            OrganizationResponse created = service.create(subject, createRequest());

            assertEquals(NAME, created.name());
            assertEquals(SLUG, created.slug());
            ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
            verify(memberships).save(saved.capture());
            assertEquals(caller, saved.getValue().getUser());
            assertEquals(Roles.OWNER, saved.getValue().getRole().getName());
        }

        @Test
        void aTakenSlugIsRejectedBeforeAnythingIsSaved() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
            when(users.findRole(Roles.OWNER)).thenReturn(new Role(Roles.OWNER));
            when(organizations.existsBySlug(SLUG)).thenReturn(true);

            assertThrows(OrganizationSlugTakenException.class, () -> service.create(subject, createRequest()));
            verify(organizations, never()).saveAndFlush(any());
            verify(memberships, never()).save(any());
        }

        @Test
        void aConcurrentDuplicateSlugIsReportedAsTaken() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
            when(users.findRole(Roles.OWNER)).thenReturn(new Role(Roles.OWNER));
            when(organizations.existsBySlug(SLUG)).thenReturn(false);
            when(organizations.saveAndFlush(any(Organization.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate slug"));

            assertThrows(OrganizationSlugTakenException.class, () -> service.create(subject, createRequest()));
            verify(memberships, never()).save(any());
        }

        @Test
        void anUnknownOrDisabledCallerIsNotAvailable() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class, () -> service.create(subject, createRequest()));
            verify(organizations, never()).saveAndFlush(any());
        }

        @Test
        void aSubjectThatIsNotAUserIdIsNotAvailable() {
            assertThrows(AccountNotAvailableException.class,
                    () -> service.create("not-a-uuid", createRequest()));
        }

        @Test
        void aMissingOwnerRoleIsAServerFaultAndNothingIsSaved() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
            when(users.findRole(Roles.OWNER)).thenThrow(new RoleNotSeededException(Roles.OWNER));

            assertThrows(RoleNotSeededException.class, () -> service.create(subject, createRequest()));
            verify(organizations, never()).saveAndFlush(any());
        }
    }

    @Nested
    class ListForCaller {

        @Test
        void returnsTheRequestedPageSortedByName() {
            Page<Membership> found = new PageImpl<>(List.of(membershipAs(Roles.AGENT)), PageRequest.of(0, 2), 3);
            when(memberships.findByUserId(eq(caller.getId()), any(Pageable.class))).thenReturn(found);

            PageResponse<OrganizationResponse> page = service.listForCaller(subject, 0, 2);

            assertEquals(1, page.content().size());
            assertEquals(NAME, page.content().get(0).name());
            assertEquals(3, page.totalElements());
            ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
            verify(memberships).findByUserId(eq(caller.getId()), asked.capture());
            assertEquals(2, asked.getValue().getPageSize());
            assertNotNull(asked.getValue().getSort().getOrderFor("organization.name"));
        }

        @Test
        void aCallerWithNoMembershipsGetsAnEmptyPage() {
            when(memberships.findByUserId(eq(caller.getId()), any(Pageable.class)))
                    .thenReturn(Page.empty());

            PageResponse<OrganizationResponse> page = service.listForCaller(subject, 0, 20);

            assertTrue(page.content().isEmpty());
            assertEquals(0, page.totalElements());
        }

        @Test
        void aSubjectThatIsNotAUserIdIsNotAvailable() {
            assertThrows(AccountNotAvailableException.class, () -> service.listForCaller("nope", 0, 20));
        }
    }

    @Nested
    class Get {

        @Test
        void aMemberGetsTheOrganizationWhateverTheirRole() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.of(membershipAs(Roles.AGENT)));

            assertEquals(NAME, service.get(subject, organizationId).name());
        }

        @Test
        void aNonMemberOrUnknownIdIsNotFound() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(OrganizationNotFoundException.class, () -> service.get(subject, organizationId));
        }
    }

    @Nested
    class Rename {

        @Test
        void anOwnerRenamesTheOrganization() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.of(membershipAs(Roles.OWNER)));

            OrganizationResponse renamed = service.rename(subject, organizationId,
                    new UpdateOrganizationRequest("  Acme Desk "));

            assertEquals("Acme Desk", renamed.name());
            assertEquals(SLUG, renamed.slug());
        }

        @Test
        void anAdminRenamesTheOrganization() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.of(membershipAs(Roles.ADMIN)));

            assertEquals("Acme Desk", service.rename(subject, organizationId,
                    new UpdateOrganizationRequest("Acme Desk")).name());
        }

        @Test
        void anAgentIsDeniedAndTheNameIsKept() {
            Membership agent = membershipAs(Roles.AGENT);
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.of(agent));

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.rename(subject, organizationId, new UpdateOrganizationRequest("Hacked")));
            assertEquals(NAME, agent.getOrganization().getName());
        }

        @Test
        void aNonMemberOrUnknownIdIsNotFound() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(OrganizationNotFoundException.class,
                    () -> service.rename(subject, organizationId, new UpdateOrganizationRequest("Acme Desk")));
        }
    }

    @Nested
    class Delete {

        @Test
        void anOwnerDeletesTheOrganization() {
            Organization target = organization();
            Membership ownership = new Membership(target, caller, new Role(Roles.OWNER));
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.of(ownership));

            service.delete(subject, organizationId);

            verify(memberships).delete(ownership);
            verify(organizations).delete(target);
        }

        @Test
        void anAdminIsDeniedAndNothingIsDeleted() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.of(membershipAs(Roles.ADMIN)));

            assertThrows(InsufficientOrganizationRoleException.class, () -> service.delete(subject, organizationId));
            verify(organizations, never()).delete(any());
        }

        @Test
        void anAgentIsDeniedAndNothingIsDeleted() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.of(membershipAs(Roles.AGENT)));

            assertThrows(InsufficientOrganizationRoleException.class, () -> service.delete(subject, organizationId));
            verify(organizations, never()).delete(any());
        }

        @Test
        void aNonMemberOrUnknownIdIsNotFound() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(OrganizationNotFoundException.class, () -> service.delete(subject, organizationId));
            verify(organizations, never()).delete(any());
        }
    }
}
