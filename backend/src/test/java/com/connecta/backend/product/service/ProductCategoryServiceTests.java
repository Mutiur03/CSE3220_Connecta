package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.product.dto.ProductCategoryRequest;
import com.connecta.backend.product.dto.ProductCategoryResponse;
import com.connecta.backend.product.repository.ProductCategoryRepository;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/** Built by hand so the real TenantAccessService and its role order run under the rules being tested. */
@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTests {

    @Mock
    ProductCategoryRepository categories;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    ProductCategoryService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = organization();
    private final User caller = TestUsers.user("caller@example.com", Roles.ADMIN);
    private final String subject = caller.getId().toString();

    @BeforeEach
    void setUp() {
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new ProductCategoryService(categories, tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
    }

    private Organization organization() {
        Organization org = new Organization("Acme", "acme");
        TestUsers.set(org, "id", organizationId);
        return org;
    }

    /** The caller holds this role in the organization. Without this call the caller is a non-member. */
    private void callerHolds(String roleName) {
        Membership mine = new Membership(organization, caller, new Role(roleName));
        TestUsers.set(mine, "id", UUID.randomUUID());
        when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId())).thenReturn(Optional.of(mine));
    }

    private ProductCategory category(String name) {
        ProductCategory category = new ProductCategory(organization, name, "desc");
        TestUsers.set(category, "id", UUID.randomUUID());
        return category;
    }

    private ProductCategoryRequest request(String name) {
        return new ProductCategoryRequest(name, "desc");
    }

    @Nested
    class ListCategories {

        @Test
        void aMemberGetsOnePageOfCategories() {
            callerHolds(Roles.AGENT);
            ProductCategory shoes = category("Shoes");
            when(categories.findByOrganizationId(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(shoes)));

            PageResponse<ProductCategoryResponse> page = service.list(subject, organizationId, 0, 20);

            assertEquals(1, page.totalElements());
            assertEquals("Shoes", page.content().get(0).name());
        }

        @Test
        void aNonMemberIsNotFound() {
            assertThrows(NotAMemberException.class, () -> service.list(subject, organizationId, 0, 20));
        }
    }

    @Nested
    class Get {

        @Test
        void aMemberGetsOneCategory() {
            callerHolds(Roles.AGENT);
            ProductCategory shoes = category("Shoes");
            when(categories.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));

            assertEquals("Shoes", service.get(subject, organizationId, shoes.getId()).name());
        }

        @Test
        void anotherTenantsCategoryIsNotFound() {
            callerHolds(Roles.AGENT);
            UUID foreignId = UUID.randomUUID();
            when(categories.findByIdAndOrganizationId(foreignId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductCategoryNotFoundException.class,
                    () -> service.get(subject, organizationId, foreignId));
        }

        @Test
        void aNonMemberIsNotFound() {
            assertThrows(NotAMemberException.class,
                    () -> service.get(subject, organizationId, UUID.randomUUID()));
        }
    }

    @Nested
    class Create {

        @Test
        void anAdminCreatesACategoryTrimmingTheName() {
            callerHolds(Roles.ADMIN);
            when(categories.existsByOrganizationIdAndName(organizationId, "Shoes")).thenReturn(false);
            when(categories.saveAndFlush(any(ProductCategory.class))).thenAnswer(call -> call.getArgument(0));

            ProductCategoryResponse created = service.create(subject, organizationId, request("  Shoes "));

            assertEquals("Shoes", created.name());
            assertEquals(organizationId, created.organizationId());
        }

        @Test
        void aDuplicateNameIsRejectedBeforeSaving() {
            callerHolds(Roles.ADMIN);
            when(categories.existsByOrganizationIdAndName(organizationId, "Shoes")).thenReturn(true);

            assertThrows(ProductCategoryNameTakenException.class,
                    () -> service.create(subject, organizationId, request("Shoes")));
            verify(categories, never()).saveAndFlush(any());
        }

        @Test
        void aConcurrentDuplicateSurfacesAsNameTaken() {
            callerHolds(Roles.ADMIN);
            when(categories.existsByOrganizationIdAndName(organizationId, "Shoes")).thenReturn(false);
            when(categories.saveAndFlush(any(ProductCategory.class)))
                    .thenThrow(new DataIntegrityViolationException("unique"));

            assertThrows(ProductCategoryNameTakenException.class,
                    () -> service.create(subject, organizationId, request("Shoes")));
        }

        @Test
        void anAgentIsForbidden() {
            callerHolds(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.create(subject, organizationId, request("Shoes")));
            verify(categories, never()).saveAndFlush(any());
        }

        @Test
        void aNonMemberIsNotFound() {
            assertThrows(NotAMemberException.class,
                    () -> service.create(subject, organizationId, request("Shoes")));
        }
    }

    @Nested
    class Update {

        @Test
        void anAdminRenamesACategory() {
            callerHolds(Roles.ADMIN);
            ProductCategory shoes = category("Shoes");
            when(categories.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));
            when(categories.existsByOrganizationIdAndName(organizationId, "Footwear")).thenReturn(false);
            when(categories.saveAndFlush(shoes)).thenReturn(shoes);

            assertEquals("Footwear",
                    service.update(subject, organizationId, shoes.getId(), request("Footwear")).name());
        }

        @Test
        void keepingTheSameNameSkipsTheUniquenessCheck() {
            callerHolds(Roles.ADMIN);
            ProductCategory shoes = category("Shoes");
            when(categories.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));
            when(categories.saveAndFlush(shoes)).thenReturn(shoes);

            service.update(subject, organizationId, shoes.getId(), request("Shoes"));

            verify(categories, never()).existsByOrganizationIdAndName(any(UUID.class), anyString());
        }

        @Test
        void aNameUsedByAnotherCategoryIsRejected() {
            callerHolds(Roles.ADMIN);
            ProductCategory shoes = category("Shoes");
            when(categories.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));
            when(categories.existsByOrganizationIdAndName(organizationId, "Bags")).thenReturn(true);

            assertThrows(ProductCategoryNameTakenException.class,
                    () -> service.update(subject, organizationId, shoes.getId(), request("Bags")));
        }

        @Test
        void anotherTenantsCategoryIsNotFound() {
            callerHolds(Roles.ADMIN);
            UUID foreignId = UUID.randomUUID();
            when(categories.findByIdAndOrganizationId(foreignId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductCategoryNotFoundException.class,
                    () -> service.update(subject, organizationId, foreignId, request("Bags")));
        }

        @Test
        void anAgentIsForbidden() {
            callerHolds(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.update(subject, organizationId, UUID.randomUUID(), request("Bags")));
        }
    }

    @Nested
    class Delete {

        @Test
        void anAdminDeletesACategory() {
            callerHolds(Roles.ADMIN);
            ProductCategory shoes = category("Shoes");
            when(categories.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));

            service.delete(subject, organizationId, shoes.getId());

            verify(categories).delete(shoes);
        }

        @Test
        void anotherTenantsCategoryIsNotFoundAndNothingIsDeleted() {
            callerHolds(Roles.ADMIN);
            UUID foreignId = UUID.randomUUID();
            when(categories.findByIdAndOrganizationId(foreignId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductCategoryNotFoundException.class,
                    () -> service.delete(subject, organizationId, foreignId));
            verify(categories, never()).delete(any());
        }

        @Test
        void anAgentIsForbidden() {
            callerHolds(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.delete(subject, organizationId, UUID.randomUUID()));
            verify(categories, never()).delete(any());
        }

        @Test
        void aNonMemberIsNotFound() {
            assertThrows(NotAMemberException.class,
                    () -> service.delete(subject, organizationId, UUID.randomUUID()));
        }
    }
}
