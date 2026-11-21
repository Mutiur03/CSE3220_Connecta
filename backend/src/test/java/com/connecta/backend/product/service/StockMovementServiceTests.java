package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.product.domain.InsufficientStockException;
import com.connecta.backend.product.domain.InvalidStockQuantityException;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.StockMovement;
import com.connecta.backend.product.domain.StockMovementType;
import com.connecta.backend.product.dto.RecordStockMovementRequest;
import com.connecta.backend.product.dto.StockMovementResponse;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.product.repository.StockMovementRepository;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.math.BigDecimal;
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
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/** Built by hand so the real TenantAccessService and ActiveCaller run under the rules being tested. */
@ExtendWith(MockitoExtension.class)
class StockMovementServiceTests {

    @Mock
    ProductRepository products;

    @Mock
    StockMovementRepository movements;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    StockMovementService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = organization();
    private final User caller = TestUsers.user("caller@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new StockMovementService(products, movements, tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
    }

    private Organization organization() {
        Organization org = new Organization("Acme", "acme");
        TestUsers.set(org, "id", organizationId);
        return org;
    }

    private void callerIsAMember() {
        Membership membership = new Membership(organization, caller, new Role(Roles.AGENT));
        TestUsers.set(membership, "id", UUID.randomUUID());
        lenient().when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(membership));
    }

    private void callerIsNotAMember() {
        lenient().when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.empty());
    }

    private Product productWithStock(int stock) {
        Product product = new Product(organization, null, "Shoe", null);
        TestUsers.set(product, "id", productId);
        TestUsers.set(product, "stockQuantity", stock);
        return product;
    }

    private void productIsLockable(Product product) {
        lenient().when(products.findForUpdateByIdAndOrganizationId(productId, organizationId))
                .thenReturn(Optional.of(product));
    }

    private void productIsNotFound() {
        lenient().when(products.findForUpdateByIdAndOrganizationId(productId, organizationId))
                .thenReturn(Optional.empty());
        lenient().when(products.findByIdAndOrganizationId(productId, organizationId))
                .thenReturn(Optional.empty());
    }

    private void savedMovementsEchoBack() {
        lenient().when(movements.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RecordStockMovementRequest request(StockMovementType type, Integer quantity, String reason) {
        return new RecordStockMovementRequest(type, quantity, reason);
    }

    @Nested
    class Record {

        @Test
        void receiveAddsStockLocksTheProductAndRecordsTheMovement() {
            callerIsAMember();
            Product product = productWithStock(4);
            productIsLockable(product);
            savedMovementsEchoBack();

            StockMovementResponse response = service.record(subject, organizationId, productId,
                    request(StockMovementType.RECEIVE, 6, "  Delivery 12  "));

            verify(products).findForUpdateByIdAndOrganizationId(productId, organizationId);
            verify(products, never()).findByIdAndOrganizationId(productId, organizationId);
            assertEquals(10, product.getStockQuantity());
            assertEquals(StockMovementType.RECEIVE, response.type());
            assertEquals(6, response.quantityDelta());
            assertEquals(10, response.quantityAfter());
            assertEquals("Delivery 12", response.reason());
            assertEquals(product.getName(), response.productName());
            assertEquals(caller.getId(), response.createdBy().id());
            assertEquals(caller.getFullName(), response.createdBy().fullName());
        }

        @Test
        void issueTakesStockAndStoresANegativeDeltaWithTheStockAfter() {
            callerIsAMember();
            productIsLockable(productWithStock(10));
            savedMovementsEchoBack();

            StockMovementResponse response = service.record(subject, organizationId, productId,
                    request(StockMovementType.ISSUE, 3, null));

            assertEquals(-3, response.quantityDelta());
            assertEquals(7, response.quantityAfter());
            assertNull(response.reason());
        }

        @Test
        void adjustSetsTheLevelAndStoresTheDifference() {
            callerIsAMember();
            productIsLockable(productWithStock(8));
            savedMovementsEchoBack();

            StockMovementResponse response = service.record(subject, organizationId, productId,
                    request(StockMovementType.ADJUST, 5, "Stock count"));

            assertEquals(-3, response.quantityDelta());
            assertEquals(5, response.quantityAfter());
        }

        @Test
        void issueBeyondTheStockIsRefusedAndNothingIsSaved() {
            callerIsAMember();
            productIsLockable(productWithStock(2));

            assertThrows(InsufficientStockException.class, () -> service.record(subject, organizationId, productId,
                    request(StockMovementType.ISSUE, 3, null)));

            verify(movements, never()).save(any(StockMovement.class));
        }

        @Test
        void aQuantityBelowTheTypeMinimumIsRefusedAndNothingIsSaved() {
            callerIsAMember();
            productIsLockable(productWithStock(2));

            assertThrows(InvalidStockQuantityException.class, () -> service.record(subject, organizationId, productId,
                    request(StockMovementType.RECEIVE, 0, null)));

            verify(movements, never()).save(any(StockMovement.class));
        }

        @Test
        void aNonMemberIsNotFoundBeforeTheProductIsLocked() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class, () -> service.record(subject, organizationId, productId,
                    request(StockMovementType.RECEIVE, 1, null)));

            verify(products, never()).findForUpdateByIdAndOrganizationId(any(), any());
        }

        @Test
        void anotherTenantsProductIsNotFound() {
            callerIsAMember();
            productIsNotFound();

            assertThrows(ProductNotFoundException.class, () -> service.record(subject, organizationId, productId,
                    request(StockMovementType.RECEIVE, 1, null)));

            verify(movements, never()).save(any(StockMovement.class));
        }
    }

    @Nested
    class ListForProduct {

        @Test
        void aMemberSeesOnePageOfTheProductsMovements() {
            callerIsAMember();
            Product product = productWithStock(0);
            when(products.findByIdAndOrganizationId(productId, organizationId)).thenReturn(Optional.of(product));
            StockMovement receive = new StockMovement(product, StockMovementType.RECEIVE, 4, 4, null, caller);
            when(movements.findByOrganizationIdAndProductId(eq(organizationId), eq(productId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(receive), PageRequest.of(0, 20), 1));

            PageResponse<StockMovementResponse> page = service.listForProduct(subject, organizationId, productId, 0, 20);

            assertEquals(1, page.totalElements());
            assertEquals(4, page.content().get(0).quantityDelta());
        }

        @Test
        void aProductOfAnotherTenantIsNotFound() {
            callerIsAMember();
            when(products.findByIdAndOrganizationId(productId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class,
                    () -> service.listForProduct(subject, organizationId, productId, 0, 20));
        }

        @Test
        void aNonMemberIsNotFound() {
            callerIsNotAMember();

            assertThrows(NotAMemberException.class,
                    () -> service.listForProduct(subject, organizationId, productId, 0, 20));
        }
    }

    @Nested
    class ListForOrganization {

        @Test
        void withoutAProductIdItListsTheWholeOrganizationNewestFirst() {
            callerIsAMember();
            when(movements.findByOrganizationId(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            PageResponse<StockMovementResponse> page = service.listForOrganization(subject, organizationId, null, 0, 20);

            assertEquals(0, page.totalElements());
            ArgumentCaptor<Pageable> sort = ArgumentCaptor.forClass(Pageable.class);
            verify(movements).findByOrganizationId(any(), sort.capture());
            assertTrue(sort.getValue().getSort().getOrderFor("createdAt").isDescending());
            assertEquals("id", sort.getValue().getSort().toList().get(1).getProperty());
        }

        @Test
        void aProductIdNarrowsToThatProductInsideTheOrganization() {
            callerIsAMember();
            when(products.findByIdAndOrganizationId(productId, organizationId))
                    .thenReturn(Optional.of(productWithStock(0)));
            when(movements.findByOrganizationIdAndProductId(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            service.listForOrganization(subject, organizationId, productId, 0, 20);

            verify(movements, never()).findByOrganizationId(any(), any());
            verify(movements).findByOrganizationIdAndProductId(any(), any(), any(Pageable.class));
        }

        @Test
        void aProductIdOfAnotherTenantIsNotFound() {
            callerIsAMember();
            when(products.findByIdAndOrganizationId(productId, organizationId)).thenReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class,
                    () -> service.listForOrganization(subject, organizationId, productId, 0, 20));
        }
    }

    @Nested
    class Get {

        @Test
        void aMemberReadsOneMovement() {
            callerIsAMember();
            StockMovement movement = new StockMovement(productWithStock(0), StockMovementType.ADJUST, 0, 0, "Check",
                    caller);
            UUID movementId = UUID.randomUUID();
            when(movements.findByIdAndOrganizationId(movementId, organizationId)).thenReturn(Optional.of(movement));

            assertEquals("Check", service.get(subject, organizationId, movementId).reason());
        }

        @Test
        void anotherTenantsMovementIsNotFound() {
            callerIsAMember();
            UUID movementId = UUID.randomUUID();
            when(movements.findByIdAndOrganizationId(movementId, organizationId)).thenReturn(Optional.empty());

            assertThrows(StockMovementNotFoundException.class,
                    () -> service.get(subject, organizationId, movementId));
        }
    }
}
