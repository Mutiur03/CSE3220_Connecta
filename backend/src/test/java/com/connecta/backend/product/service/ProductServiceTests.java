package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.product.dto.CreateProductRequest;
import com.connecta.backend.product.dto.ProductResponse;
import com.connecta.backend.product.dto.UpdateProductRequest;
import com.connecta.backend.product.repository.ProductCategoryRepository;
import com.connecta.backend.product.repository.ProductRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/** Built by hand so the real TenantAccessService and its role order run under the rules being tested. */
@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

    @Mock
    ProductRepository products;

    @Mock
    ProductCategoryRepository categories;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    ProductService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = organization();
    private final User caller = TestUsers.user("caller@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();

    @BeforeEach
    void setUp() {
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new ProductService(products, categories, tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
    }

    private Organization organization() {
        Organization org = new Organization("Acme", "acme");
        TestUsers.set(org, "id", organizationId);
        return org;
    }

    /** The caller holds this role in the organization. */
    private void callerIs(String roleName) {
        Membership membership = new Membership(organization, caller, new Role(roleName));
        TestUsers.set(membership, "id", UUID.randomUUID());
        lenient().when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(membership));
    }

    private void callerIsNotAMember() {
        lenient().when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.empty());
    }

    private Product product(String name, ProductCategory category) {
        Product product = new Product(organization, category, name, "A product");
        TestUsers.set(product, "id", UUID.randomUUID());
        return product;
    }

    private ProductCategory category(String name) {
        ProductCategory category = new ProductCategory(organization, name, null);
        TestUsers.set(category, "id", UUID.randomUUID());
        return category;
    }

    @Nested
    class ListProducts {

        @Test
        void aMemberSeesAPageOfTheOrganizationsProducts() {
            callerIs(Roles.AGENT);
            Product shoes = product("Shoes", null);
            when(products.findByOrganizationId(eq(organizationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(shoes), PageRequest.of(0, 20), 1));

            PageResponse<ProductResponse> page = service.list(subject, organizationId, 0, 20);

            assertEquals(1, page.content().size());
            assertEquals("Shoes", page.content().get(0).name());
            assertEquals(1, page.totalElements());
        }

        @Test
        void sortsByNameThenIdSoPagesStayStable() {
            callerIs(Roles.AGENT);
            when(products.findByOrganizationId(eq(organizationId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.list(subject, organizationId, 2, 50);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(products).findByOrganizationId(eq(organizationId), pageable.capture());
            assertEquals(2, pageable.getValue().getPageNumber());
            assertEquals(50, pageable.getValue().getPageSize());
            assertEquals(Sort.by("name").ascending().and(Sort.by("id")), pageable.getValue().getSort());
        }

        @Test
        void aNonMemberGetsNotAMember() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class, () -> service.list(subject, organizationId, 0, 20));
            verify(products, never()).findByOrganizationId(any(), any(Pageable.class));
        }
    }

    @Nested
    class GetProduct {

        @Test
        void aMemberGetsOneProduct() {
            callerIs(Roles.AGENT);
            Product shoes = product("Shoes", null);
            when(products.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));

            ProductResponse response = service.get(subject, organizationId, shoes.getId());

            assertEquals(shoes.getId(), response.id());
            assertEquals("Shoes", response.name());
        }

        @Test
        void anUnknownOrAnotherTenantsProductIsNotFound() {
            callerIs(Roles.AGENT);
            UUID productId = UUID.randomUUID();
            when(products.findByIdAndOrganizationId(productId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class, () -> service.get(subject, organizationId, productId));
        }

        @Test
        void aNonMemberGetsNotAMember() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class,
                    () -> service.get(subject, organizationId, UUID.randomUUID()));
        }
    }

    @Nested
    class CreateProduct {

        @Test
        void anAdminCreatesAProductWithoutACategory() {
            callerIs(Roles.ADMIN);
            when(products.existsByOrganizationIdAndName(organizationId, "Shoes")).thenReturn(false);
            when(products.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));

            ProductResponse response = service.create(subject, organizationId,
                    new CreateProductRequest("  Shoes  ", "Running", null));

            ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
            verify(products).save(saved.capture());
            assertEquals("Shoes", saved.getValue().getName());
            assertEquals(organization, saved.getValue().getOrganization());
            assertNull(response.categoryId());
        }

        @Test
        void anAdminCreatesAProductInACategoryOfTheSameOrganization() {
            callerIs(Roles.ADMIN);
            ProductCategory footwear = category("Footwear");
            when(products.existsByOrganizationIdAndName(organizationId, "Shoes")).thenReturn(false);
            when(categories.findByIdAndOrganizationId(footwear.getId(), organizationId))
                    .thenReturn(Optional.of(footwear));
            when(products.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));

            ProductResponse response = service.create(subject, organizationId,
                    new CreateProductRequest("Shoes", null, footwear.getId()));

            assertEquals(footwear.getId(), response.categoryId());
            assertEquals("Footwear", response.categoryName());
        }

        @Test
        void aDuplicateNameIsAConflictAndNothingIsSaved() {
            callerIs(Roles.ADMIN);
            when(products.existsByOrganizationIdAndName(organizationId, "Shoes")).thenReturn(true);

            assertThrows(ProductNameTakenException.class, () -> service.create(subject, organizationId,
                    new CreateProductRequest("Shoes", null, null)));
            verify(products, never()).save(any(Product.class));
        }

        @Test
        void aCategoryOfAnotherOrganizationIsNotFoundAndNothingIsSaved() {
            callerIs(Roles.ADMIN);
            UUID otherTenantsCategory = UUID.randomUUID();
            when(products.existsByOrganizationIdAndName(organizationId, "Shoes")).thenReturn(false);
            when(categories.findByIdAndOrganizationId(otherTenantsCategory, organizationId))
                    .thenReturn(Optional.empty());

            assertThrows(ProductCategoryNotFoundException.class, () -> service.create(subject, organizationId,
                    new CreateProductRequest("Shoes", null, otherTenantsCategory)));
            verify(products, never()).save(any(Product.class));
        }

        @Test
        void anAgentIsForbiddenAndNothingIsSaved() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class, () -> service.create(subject, organizationId,
                    new CreateProductRequest("Shoes", null, null)));
            verify(products, never()).save(any(Product.class));
        }

        @Test
        void aNonMemberGetsNotAMember() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class, () -> service.create(subject, organizationId,
                    new CreateProductRequest("Shoes", null, null)));
        }
    }

    @Nested
    class UpdateProduct {

        @Test
        void anAdminRenamesAndMovesAProduct() {
            callerIs(Roles.ADMIN);
            ProductCategory footwear = category("Footwear");
            Product shoes = product("Shoes", null);
            when(products.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));
            when(products.existsByOrganizationIdAndName(organizationId, "Running shoes")).thenReturn(false);
            when(categories.findByIdAndOrganizationId(footwear.getId(), organizationId))
                    .thenReturn(Optional.of(footwear));

            ProductResponse response = service.update(subject, organizationId, shoes.getId(),
                    new UpdateProductRequest("Running shoes", "Light", footwear.getId()));

            assertEquals("Running shoes", response.name());
            assertEquals(footwear.getId(), response.categoryId());
        }

        @Test
        void keepingTheSameNameDoesNotCheckForDuplicates() {
            callerIs(Roles.ADMIN);
            Product shoes = product("Shoes", null);
            when(products.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));

            service.update(subject, organizationId, shoes.getId(), new UpdateProductRequest("Shoes", "New text", null));

            verify(products, never()).existsByOrganizationIdAndName(any(), anyString());
        }

        @Test
        void renamingToAnotherProductsNameIsAConflict() {
            callerIs(Roles.ADMIN);
            Product shoes = product("Shoes", null);
            when(products.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));
            when(products.existsByOrganizationIdAndName(organizationId, "Hats")).thenReturn(true);

            assertThrows(ProductNameTakenException.class, () -> service.update(subject, organizationId,
                    shoes.getId(), new UpdateProductRequest("Hats", null, null)));
        }

        @Test
        void aNullCategoryRemovesTheProductFromItsCategory() {
            callerIs(Roles.ADMIN);
            Product shoes = product("Shoes", category("Footwear"));
            when(products.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));

            ProductResponse response = service.update(subject, organizationId, shoes.getId(),
                    new UpdateProductRequest("Shoes", null, null));

            assertNull(response.categoryId());
        }

        @Test
        void anUnknownOrAnotherTenantsProductIsNotFound() {
            callerIs(Roles.ADMIN);
            UUID productId = UUID.randomUUID();
            when(products.findByIdAndOrganizationId(productId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class, () -> service.update(subject, organizationId, productId,
                    new UpdateProductRequest("Shoes", null, null)));
        }

        @Test
        void aMissingCategoryIsNotFound() {
            callerIs(Roles.ADMIN);
            Product shoes = product("Shoes", null);
            UUID missing = UUID.randomUUID();
            when(products.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));
            when(categories.findByIdAndOrganizationId(missing, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductCategoryNotFoundException.class, () -> service.update(subject, organizationId,
                    shoes.getId(), new UpdateProductRequest("Shoes", null, missing)));
        }

        @Test
        void anAgentIsForbidden() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class, () -> service.update(subject, organizationId,
                    UUID.randomUUID(), new UpdateProductRequest("Shoes", null, null)));
            verify(products, never()).findByIdAndOrganizationId(any(), any());
        }
    }

    @Nested
    class DeleteProduct {

        @Test
        void anAdminDeletesAProduct() {
            callerIs(Roles.ADMIN);
            Product shoes = product("Shoes", null);
            when(products.findByIdAndOrganizationId(shoes.getId(), organizationId)).thenReturn(Optional.of(shoes));

            service.delete(subject, organizationId, shoes.getId());

            verify(products).delete(shoes);
        }

        @Test
        void anUnknownOrAnotherTenantsProductIsNotFoundAndNothingIsDeleted() {
            callerIs(Roles.ADMIN);
            UUID productId = UUID.randomUUID();
            when(products.findByIdAndOrganizationId(productId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class, () -> service.delete(subject, organizationId, productId));
            verify(products, never()).delete(any(Product.class));
        }

        @Test
        void anAgentIsForbiddenAndNothingIsDeleted() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.delete(subject, organizationId, UUID.randomUUID()));
            verify(products, never()).delete(any(Product.class));
        }

        @Test
        void aNonMemberGetsNotAMember() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class,
                    () -> service.delete(subject, organizationId, UUID.randomUUID()));
        }
    }
}
