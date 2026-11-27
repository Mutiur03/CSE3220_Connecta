package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.ProductImageProperties;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.InsufficientOrganizationRoleException;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductImage;
import com.connecta.backend.product.dto.ProductImageResponse;
import com.connecta.backend.product.repository.ProductImageRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/** Built by hand so the real TenantAccessService and its role order run under the rules being tested. */
@ExtendWith(MockitoExtension.class)
class ProductImageServiceTests {

    private static final int MAX_SIZE = 100;
    private static final int MAX_PER_PRODUCT = 2;

    @Mock
    ProductImageRepository images;

    @Mock
    ProductRepository products;

    @Mock
    ProductImageStorage storage;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    ProductImageService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = organization();
    private final Product product = product();
    private final User caller = TestUsers.user("caller@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();

    @BeforeEach
    void setUp() {
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new ProductImageService(images, products, storage,
                new ProductImageProperties(MAX_SIZE, MAX_PER_PRODUCT), tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
        lenient().when(products.findByIdAndOrganizationId(product.getId(), organizationId))
                .thenReturn(Optional.of(product));
        lenient().when(products.findForUpdateByIdAndOrganizationId(product.getId(), organizationId))
                .thenReturn(Optional.of(product));
    }

    private Organization organization() {
        Organization org = new Organization("Acme", "acme");
        TestUsers.set(org, "id", organizationId);
        return org;
    }

    private Product product() {
        Product p = new Product(organization, null, "Shoes", "A product");
        TestUsers.set(p, "id", UUID.randomUUID());
        return p;
    }

    private ProductImage image(String key, String contentType, int position) {
        ProductImage image = new ProductImage(product, key, contentType, 10, position);
        TestUsers.set(image, "id", UUID.randomUUID());
        return image;
    }

    private void callerIs(String roleName) {
        Membership membership = new Membership(organization, caller, new Role(roleName));
        TestUsers.set(membership, "id", UUID.randomUUID());
        lenient().when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(membership));
    }

    private ProductImageResponse upload(String declaredType, byte[] content) {
        return service.upload(subject, organizationId, product.getId(), declaredType, content);
    }

    @Nested
    class Upload {

        @BeforeEach
        void admin() {
            callerIs(Roles.ADMIN);
        }

        @Test
        void anAdminStoresAValidImageAtTheNextPosition() {
            when(images.countByProductIdAndOrganizationId(product.getId(), organizationId)).thenReturn(1L);
            when(images.findMaxPosition(product.getId(), organizationId)).thenReturn(4);
            when(storage.store(organizationId, product.getId(), "image/png", ProductImageSamples.PNG))
                    .thenReturn("organizations/x/products/y/a.png");
            when(images.save(any(ProductImage.class))).thenAnswer(call -> {
                ProductImage saved = call.getArgument(0);
                TestUsers.set(saved, "id", UUID.randomUUID());
                return saved;
            });

            ProductImageResponse response = upload("image/png", ProductImageSamples.PNG);

            ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
            verify(images).save(saved.capture());
            assertEquals("organizations/x/products/y/a.png", saved.getValue().getObjectKey());
            assertEquals(5, saved.getValue().getPosition());
            assertEquals(ProductImageSamples.PNG.length, saved.getValue().getSizeBytes());
            assertEquals("image/png", response.contentType());
            assertEquals("/api/organizations/" + organizationId + "/products/" + product.getId() + "/images/"
                    + response.id() + "/content", response.contentPath());
        }

        @Test
        void aContentTypeParameterIsIgnored() {
            when(images.save(any(ProductImage.class))).thenAnswer(call -> call.getArgument(0));
            when(storage.store(any(), any(), anyString(), any())).thenReturn("k");

            assertEquals("image/jpeg", upload("Image/JPEG; charset=binary", ProductImageSamples.JPEG).contentType());
        }

        @Test
        void aFileOverTheSizeLimitIsRejectedBeforeAnythingIsStored() {
            assertThrows(ProductImageTooLargeException.class, () -> upload("image/png", new byte[MAX_SIZE + 1]));
            verify(storage, never()).store(any(), any(), anyString(), any());
        }

        @Test
        void aDeclaredTypeOutsideTheAllowedListIsRejected() {
            assertThrows(UnsupportedProductImageTypeException.class,
                    () -> upload("image/gif", ProductImageSamples.PNG));
            assertThrows(UnsupportedProductImageTypeException.class, () -> upload(null, ProductImageSamples.PNG));
            verify(storage, never()).store(any(), any(), anyString(), any());
        }

        @Test
        void bytesThatDoNotMatchTheDeclaredTypeAreRejected() {
            assertThrows(UnsupportedProductImageTypeException.class,
                    () -> upload("image/png", ProductImageSamples.HTML));
            assertThrows(UnsupportedProductImageTypeException.class,
                    () -> upload("image/png", ProductImageSamples.JPEG));
            assertThrows(UnsupportedProductImageTypeException.class, () -> upload("image/png", new byte[0]));
            verify(storage, never()).store(any(), any(), anyString(), any());
        }

        @Test
        void aProductAtTheImageLimitRejectsAnotherImage() {
            when(images.countByProductIdAndOrganizationId(product.getId(), organizationId))
                    .thenReturn((long) MAX_PER_PRODUCT);

            assertThrows(ProductImageLimitReachedException.class, () -> upload("image/webp", ProductImageSamples.WEBP));
            verify(storage, never()).store(any(), any(), anyString(), any());
        }

        @Test
        void aFailedRowSaveRemovesTheJustStoredObject() {
            when(storage.store(any(), any(), anyString(), any())).thenReturn("organizations/x/orphan.png");
            when(images.save(any(ProductImage.class))).thenThrow(new IllegalStateException("db down"));

            assertThrows(IllegalStateException.class, () -> upload("image/png", ProductImageSamples.PNG));
            verify(storage).delete("organizations/x/orphan.png");
        }

        @Test
        void theProductRowIsLockedBeforeTheLimitIsCounted() {
            when(images.countByProductIdAndOrganizationId(product.getId(), organizationId)).thenReturn(0L);
            when(storage.store(any(), any(), anyString(), any())).thenReturn("k");
            when(images.save(any(ProductImage.class))).thenAnswer(call -> call.getArgument(0));

            upload("image/png", ProductImageSamples.PNG);

            InOrder order = inOrder(products, images);
            order.verify(products).findForUpdateByIdAndOrganizationId(product.getId(), organizationId);
            order.verify(images).countByProductIdAndOrganizationId(product.getId(), organizationId);
        }

        @Test
        void anotherTenantsProductIsNotFound() {
            UUID foreign = UUID.randomUUID();
            when(products.findForUpdateByIdAndOrganizationId(foreign, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class,
                    () -> service.upload(subject, organizationId, foreign, "image/png", ProductImageSamples.PNG));
        }

        @Test
        void anAgentIsForbidden() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> upload("image/png", ProductImageSamples.PNG));
            verify(storage, never()).store(any(), any(), anyString(), any());
        }

        @Test
        void aNonMemberGetsNotAMember() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class, () -> upload("image/png", ProductImageSamples.PNG));
        }
    }

    @Nested
    class FirstImagePaths {

        @Test
        void keepsTheFirstImageOfEachProductFromOneQuery() {
            callerIs(Roles.AGENT);
            ProductImage first = image("a", "image/png", 0);
            ProductImage second = image("b", "image/png", 1);
            when(images.findByProductIdInAndOrganizationIdOrderByPositionAscIdAsc(List.of(product.getId()),
                    organizationId)).thenReturn(List.of(first, second));

            Map<UUID, String> paths = service.firstImagePaths(subject, organizationId, List.of(product.getId()));

            assertEquals(Map.of(product.getId(), "/api/organizations/" + organizationId + "/products/"
                    + product.getId() + "/images/" + first.getId() + "/content"), paths);
        }

        @Test
        void noProductsMeansNoQuery() {
            callerIs(Roles.AGENT);

            assertEquals(Map.of(), service.firstImagePaths(subject, organizationId, List.of()));
            verify(images, never()).findByProductIdInAndOrganizationIdOrderByPositionAscIdAsc(any(), any());
        }

        @Test
        void aNonMemberGetsNotAMember() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class,
                    () -> service.firstImagePaths(subject, organizationId, List.of(product.getId())));
        }
    }

    @Nested
    class ListImages {

        @Test
        void aMemberSeesTheImagesInOrder() {
            callerIs(Roles.AGENT);
            when(images.findByProductIdAndOrganizationIdOrderByPositionAscIdAsc(product.getId(), organizationId))
                    .thenReturn(List.of(image("a", "image/png", 0), image("b", "image/jpeg", 1)));

            List<ProductImageResponse> found = service.list(subject, organizationId, product.getId());

            assertEquals(List.of(0, 1), found.stream().map(ProductImageResponse::position).toList());
        }

        @Test
        void aNonMemberGetsNotAMember() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class, () -> service.list(subject, organizationId, product.getId()));
        }

        @Test
        void anotherTenantsProductIsNotFound() {
            callerIs(Roles.AGENT);
            UUID foreign = UUID.randomUUID();
            when(products.findByIdAndOrganizationId(foreign, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class, () -> service.list(subject, organizationId, foreign));
        }
    }

    @Nested
    class Content {

        @Test
        void aMemberReadsTheBytesWithTheStoredContentType() {
            callerIs(Roles.AGENT);
            ProductImage stored = image("organizations/x/a.png", "image/png", 0);
            when(images.findByIdAndProductIdAndOrganizationId(stored.getId(), product.getId(), organizationId))
                    .thenReturn(Optional.of(stored));
            when(storage.read(organizationId, "organizations/x/a.png"))
                    .thenReturn(Optional.of(ProductImageSamples.PNG));

            ProductImageContent content = service.content(subject, organizationId, product.getId(), stored.getId());

            assertEquals("image/png", content.contentType());
            assertArrayEquals(ProductImageSamples.PNG, content.bytes());
        }

        @Test
        void anUnknownOrAnotherTenantsImageIsNotFound() {
            callerIs(Roles.AGENT);
            UUID imageId = UUID.randomUUID();
            when(images.findByIdAndProductIdAndOrganizationId(imageId, product.getId(), organizationId))
                    .thenReturn(Optional.empty());

            assertThrows(ProductImageNotFoundException.class,
                    () -> service.content(subject, organizationId, product.getId(), imageId));
        }

        @Test
        void aRowWhoseObjectIsMissingFromTheStoreIsNotFound() {
            callerIs(Roles.AGENT);
            ProductImage stored = image("organizations/x/gone.png", "image/png", 0);
            when(images.findByIdAndProductIdAndOrganizationId(stored.getId(), product.getId(), organizationId))
                    .thenReturn(Optional.of(stored));
            when(storage.read(organizationId, "organizations/x/gone.png")).thenReturn(Optional.empty());

            assertThrows(ProductImageNotFoundException.class,
                    () -> service.content(subject, organizationId, product.getId(), stored.getId()));
        }
    }

    @Nested
    class Delete {

        @Test
        void anAdminDeletesTheObjectAndTheRow() {
            callerIs(Roles.ADMIN);
            ProductImage stored = image("organizations/x/a.png", "image/png", 0);
            when(images.findByIdAndProductIdAndOrganizationId(stored.getId(), product.getId(), organizationId))
                    .thenReturn(Optional.of(stored));

            service.delete(subject, organizationId, product.getId(), stored.getId());

            verify(storage).delete("organizations/x/a.png");
            verify(images).delete(stored);
        }

        @Test
        void anAgentIsForbiddenAndNothingIsDeleted() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class,
                    () -> service.delete(subject, organizationId, product.getId(), UUID.randomUUID()));
            verify(storage, never()).delete(anyString());
            verify(images, never()).delete(any(ProductImage.class));
        }

        @Test
        void anotherTenantsImageIsNotFoundAndNothingIsDeleted() {
            callerIs(Roles.ADMIN);
            UUID imageId = UUID.randomUUID();
            when(images.findByIdAndProductIdAndOrganizationId(imageId, product.getId(), organizationId))
                    .thenReturn(Optional.empty());

            assertThrows(ProductImageNotFoundException.class,
                    () -> service.delete(subject, organizationId, product.getId(), imageId));
            verify(storage, never()).delete(anyString());
        }
    }
}
