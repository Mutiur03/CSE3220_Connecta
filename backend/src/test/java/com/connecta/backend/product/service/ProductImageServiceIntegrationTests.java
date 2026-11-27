package com.connecta.backend.product.service;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.repository.ProductImageRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Two uploads racing for the last image slot of one product, on real PostgreSQL. The first upload holds the product
 * row lock while its object is being stored; the second must wait for that lock, then see the committed image.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "app.product-images.max-per-product=1"})
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ProductImageServiceIntegrationTests extends SharedPostgres {

    private static final long TIMEOUT_SECONDS = 10;
    private static final long PARKED_WINDOW_SECONDS = 2;

    @MockitoBean
    ProductImageStorage storage;

    @Autowired
    ProductImageService service;

    @Autowired
    ProductImageRepository images;

    @Autowired
    ProductRepository products;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Test
    void twoConcurrentUploadsNeverExceedTheImageLimit() throws Exception {
        Organization org = organizations.saveAndFlush(new Organization("Acme", "acme-" + UUID.randomUUID()));
        User admin = users.saveAndFlush(newUser());
        memberships.saveAndFlush(new Membership(org, admin, roles.findByName(Roles.ADMIN).orElseThrow()));
        Product shoes = products.saveAndFlush(new Product(org, null, "Shoes", "A product"));
        String subject = admin.getId().toString();

        CountDownLatch storing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger keys = new AtomicInteger();
        when(storage.store(any(), any(), anyString(), any())).thenAnswer(call -> {
            storing.countDown();
            if (!release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release never came");
            }
            return "key-" + keys.incrementAndGet();
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> upload(subject, org.getId(), shoes.getId()));
            assertTrue(storing.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "first upload never reached storage");
            Future<String> second = pool.submit(() -> upload(subject, org.getId(), shoes.getId()));
            // With the lock the second upload is parked until the first commits, so it cannot finish in this window.
            // Without the lock it counts zero images and finishes at once while the first is still storing.
            boolean finishedWhileFirstStores = finishesWithin(second, PARKED_WINDOW_SECONDS);
            release.countDown();
            assertFalse(finishedWhileFirstStores, "second upload ran while the first still held the product lock");

            List<String> outcomes = List.of(first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).stream().sorted().toList();
            assertEquals(List.of("limit", "stored"), outcomes);
            assertEquals(1, images.countByProductIdAndOrganizationId(shoes.getId(), org.getId()));
        } finally {
            pool.shutdownNow();
        }
    }

    /** True when the task ends within the window; a task still running at the end of it is parked on the lock. */
    private boolean finishesWithin(Future<String> task, long seconds) throws InterruptedException {
        try {
            task.get(seconds, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException e) {
            return true;
        }
    }

    /** Returns "stored" or "limit" so the test can compare outcomes without catching exceptions on the pool. */
    private String upload(String subject, UUID organizationId, UUID productId) {
        try {
            service.upload(subject, organizationId, productId, "image/png", ProductImageSamples.PNG);
            return "stored";
        } catch (ProductImageLimitReachedException e) {
            return "limit";
        }
    }

    private User newUser() {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(roles.findByName(Roles.AGENT).orElseThrow());
        return user;
    }
}
