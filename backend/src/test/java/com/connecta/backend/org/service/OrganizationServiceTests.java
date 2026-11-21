package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.auth.service.AccountNotAvailableException;
import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.dto.OrganizationSummaryResponse;
import com.connecta.backend.org.repository.OrganizationSummary;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    TenantAccessService tenants;

    OrganizationService service;

    private final User caller = TestUsers.user("jane@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();
    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void callerIsEnabled() {
        service = new OrganizationService(organizations, memberships, users, tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
    }

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
        void savesTheOrganizationAndMakesTheCallerAdmin() {
            Role admin = new Role(Roles.ADMIN);
            when(users.findRole(Roles.ADMIN)).thenReturn(admin);
            when(organizations.existsBySlug(SLUG)).thenReturn(false);
            when(organizations.saveAndFlush(any(Organization.class))).thenAnswer(call -> call.getArgument(0));
            when(memberships.save(any(Membership.class))).thenAnswer(call -> call.getArgument(0));

            OrganizationResponse created = service.create(subject, createRequest());

            assertEquals(NAME, created.name());
            assertEquals(SLUG, created.slug());
            assertEquals(Roles.ADMIN, created.callerRole());
            ArgumentCaptor<Membership> saved = ArgumentCaptor.forClass(Membership.class);
            verify(memberships).save(saved.capture());
            assertEquals(caller, saved.getValue().getUser());
            assertEquals(Roles.ADMIN, saved.getValue().getRole().getName());
        }

        @Test
        void aTakenSlugIsRejectedBeforeAnythingIsSaved() {
            when(users.findRole(Roles.ADMIN)).thenReturn(new Role(Roles.ADMIN));
            when(organizations.existsBySlug(SLUG)).thenReturn(true);

            assertThrows(OrganizationSlugTakenException.class, () -> service.create(subject, createRequest()));
            verify(organizations, never()).saveAndFlush(any());
            verify(memberships, never()).save(any());
        }

        @Test
        void aConcurrentDuplicateSlugIsReportedAsTaken() {
            when(users.findRole(Roles.ADMIN)).thenReturn(new Role(Roles.ADMIN));
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
        void aMissingAdminRoleIsAServerFaultAndNothingIsSaved() {
            when(users.findRole(Roles.ADMIN)).thenThrow(new RoleNotSeededException(Roles.ADMIN));

            assertThrows(RoleNotSeededException.class, () -> service.create(subject, createRequest()));
            verify(organizations, never()).saveAndFlush(any());
        }
    }

    @Nested
    class ListAll {

        @Test
        void returnsTheRequestedPageOfSummariesSortedByName() {
            OrganizationSummary summary = org.mockito.Mockito.mock(OrganizationSummary.class);
            when(summary.getId()).thenReturn(organizationId);
            when(summary.getName()).thenReturn(NAME);
            when(summary.getSlug()).thenReturn(SLUG);
            when(summary.getMemberCount()).thenReturn(3L);
            when(organizations.findAllSummaries(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(1, 5), 11));

            PageResponse<OrganizationSummaryResponse> page = service.listAll(1, 5);

            assertEquals(NAME, page.content().get(0).name());
            assertEquals(3L, page.content().get(0).memberCount());
            assertEquals(1, page.page());
            assertEquals(11L, page.totalElements());
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(organizations).findAllSummaries(pageable.capture());
            assertEquals(1, pageable.getValue().getPageNumber());
            assertEquals(5, pageable.getValue().getPageSize());
            assertEquals("name: ASC", pageable.getValue().getSort().iterator().next().toString());
        }
    }

    @Nested
    class ListForCaller {

        @Test
        void returnsTheRequestedPageSortedByNameWithTheCallerRole() {
            Page<Membership> found = new PageImpl<>(List.of(membershipAs(Roles.AGENT)), PageRequest.of(0, 2), 3);
            when(memberships.findByUserId(eq(caller.getId()), any(Pageable.class))).thenReturn(found);

            PageResponse<OrganizationResponse> page = service.listForCaller(subject, 0, 2);

            assertEquals(1, page.content().size());
            assertEquals(NAME, page.content().get(0).name());
            assertEquals(Roles.AGENT, page.content().get(0).callerRole());
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

        @Test
        void aDisabledCallerIsNotAvailableAndNothingIsListed() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class, () -> service.listForCaller(subject, 0, 20));
            verify(memberships, never()).findByUserId(any(), any());
        }
    }

    @Nested
    class Get {

        @Test
        void aMemberGetsTheOrganizationWhateverTheirRole() {
            when(tenants.requireMembership(organizationId, caller.getId()))
                    .thenReturn(membershipAs(Roles.AGENT));

            OrganizationResponse found = service.get(subject, organizationId);

            assertEquals(NAME, found.name());
            assertEquals(Roles.AGENT, found.callerRole());
        }

        @Test
        void aNonMemberOrUnknownIdIsNotFound() {
            when(tenants.requireMembership(organizationId, caller.getId()))
                    .thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class, () -> service.get(subject, organizationId));
        }

        @Test
        void aDisabledCallerIsNotAvailableAndTenantsAreNotConsulted() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class, () -> service.get(subject, organizationId));
            verify(tenants, never()).requireMembership(any(), any());
        }
    }

    @Nested
    class Rename {

        @Test
        void anAdminRenamesTheOrganization() {
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN))
                    .thenReturn(membershipAs(Roles.ADMIN));

            OrganizationResponse renamed = service.rename(subject, organizationId,
                    new UpdateOrganizationRequest("  Acme Desk "));

            assertEquals("Acme Desk", renamed.name());
            assertEquals(SLUG, renamed.slug());
        }

        @Test
        void aRenameRequiresAtLeastAdmin() {
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.rename(subject, organizationId, new UpdateOrganizationRequest("Hacked")));
            verify(tenants).requireRole(organizationId, caller.getId(), Roles.ADMIN);
        }

        @Test
        void aNonMemberOrUnknownIdIsNotFound() {
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN))
                    .thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class,
                    () -> service.rename(subject, organizationId, new UpdateOrganizationRequest("Acme Desk")));
        }

        @Test
        void aDisabledCallerIsNotAvailableAndNothingIsRenamed() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class,
                    () -> service.rename(subject, organizationId, new UpdateOrganizationRequest("Acme Desk")));
            verify(tenants, never()).requireRole(any(), any(), any());
        }
    }

    @Nested
    class Delete {

        @Test
        void anAdminDeletesTheOrganizationAndTheCallersMembership() {
            Organization target = organization();
            Membership ownership = new Membership(target, caller, new Role(Roles.ADMIN));
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN)).thenReturn(ownership);

            service.delete(subject, organizationId);

            verify(memberships).delete(ownership);
            verify(organizations).delete(target);
        }

        @Test
        void anAgentIsDeniedAndNothingIsDeleted() {
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN))
                    .thenThrow(new InsufficientOrganizationRoleException());

            assertThrows(InsufficientOrganizationRoleException.class, () -> service.delete(subject, organizationId));
            verify(organizations, never()).delete(any());
        }

        @Test
        void aNonMemberOrUnknownIdIsNotFound() {
            when(tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN))
                    .thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class, () -> service.delete(subject, organizationId));
            verify(organizations, never()).delete(any());
        }

        @Test
        void aDisabledCallerIsNotAvailableAndNothingIsDeleted() {
            when(users.findEnabledById(caller.getId())).thenReturn(Optional.empty());

            assertThrows(AccountNotAvailableException.class, () -> service.delete(subject, organizationId));
            verify(tenants, never()).requireRole(any(), any(), any());
            verify(organizations, never()).delete(any());
        }
    }
}
