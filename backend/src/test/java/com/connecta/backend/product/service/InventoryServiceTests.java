package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.dto.InventorySummaryResponse;
import com.connecta.backend.product.repository.InventoryTotals;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/**
 * The summary math. The product counts come from the same StockStatus specifications the list uses; the
 * repository tests prove those predicates agree with the rule. Here each count is stubbed in call order:
 * all products, then low stock, then out of stock.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTests {

    @Mock
    ProductRepository products;

    @Mock
    MembershipRepository memberships;

    @Mock
    UserService users;

    InventoryService service;

    private final UUID organizationId = UUID.randomUUID();
    private final User caller = TestUsers.user("caller@example.com", Roles.AGENT);
    private final String subject = caller.getId().toString();

    @BeforeEach
    void setUp() {
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new InventoryService(products, tenants, new ActiveCaller(users));
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
    }

    private void callerIsAMember() {
        Organization organization = new Organization("Acme", "acme");
        TestUsers.set(organization, "id", organizationId);
        Membership membership = new Membership(organization, caller, new Role(Roles.AGENT));
        lenient().when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(membership));
    }

    @SuppressWarnings("unchecked")
    private static Specification<Product> anySpec() {
        return any(Specification.class);
    }

    @Nested
    class Summary {

        @Test
        void addsUpTheTotalsAndCountsLowAndOutOfStockProducts() {
            callerIsAMember();
            when(products.sumStock(organizationId)).thenReturn(new InventoryTotals(12L, new BigDecimal("47.50")));
            when(products.count(anySpec())).thenReturn(5L, 2L, 1L);

            InventorySummaryResponse summary = service.summary(subject, organizationId);

            assertEquals(new InventorySummaryResponse(5, 12, new BigDecimal("47.50"), 2, 1), summary);
        }

        @Test
        void anOrganizationWithNoProductsReadsAsZeros() {
            callerIsAMember();
            when(products.sumStock(organizationId)).thenReturn(new InventoryTotals(null, null));
            when(products.count(anySpec())).thenReturn(0L, 0L, 0L);

            InventorySummaryResponse summary = service.summary(subject, organizationId);

            assertEquals(new InventorySummaryResponse(0, 0, new BigDecimal("0.00"), 0, 0), summary);
        }

        @Test
        void stockValueIsRoundedToTwoPlaces() {
            callerIsAMember();
            when(products.sumStock(organizationId)).thenReturn(new InventoryTotals(3L, new BigDecimal("10.005")));
            when(products.count(anySpec())).thenReturn(1L, 0L, 0L);

            assertEquals(new BigDecimal("10.01"), service.summary(subject, organizationId).stockValue());
        }

        @Test
        void aNonMemberIsNotFound() {
            lenient().when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class, () -> service.summary(subject, organizationId));
        }
    }
}
