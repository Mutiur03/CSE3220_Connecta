package com.connecta.backend.product.controller;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.repository.ProductImageRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.product.service.ProductImageSamples;
import com.connecta.backend.product.service.ProductImageStorage;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The product image endpoints against real PostgreSQL (Flyway runs the migrations) and the in-memory image store.
 * Each endpoint has an allowed and a denied role, plus 401 and the cross-tenant 404.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "app.product-images.max-size-bytes=1000",
        "app.product-images.max-per-product=2"})
@AutoConfigureMockMvc
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
class ProductImageControllerIntegrationTests extends SharedPostgres {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductImageRepository images;

    @Autowired
    ProductImageStorage storage;

    private static String imagesPath(Organization organization, Product product) {
        return "/api/organizations/" + organization.getId() + "/products/" + product.getId() + "/images";
    }

    private static MockMultipartFile file(String contentType, byte[] bytes) {
        return new MockMultipartFile("file", "client-name.png", contentType, bytes);
    }

    private User newUser() {
        User user = new User("u" + UUID.randomUUID() + "@example.com", "hash", "Test User");
        user.addRole(role(Roles.AGENT));
        return users.saveAndFlush(user);
    }

    private Role role(String name) {
        return roles.findByName(name).orElseThrow();
    }

    private Organization organization(String name) {
        return organizations.saveAndFlush(new Organization(name, "org-" + UUID.randomUUID()));
    }

    private void join(Organization organization, User user, String roleName) {
        memberships.saveAndFlush(new Membership(organization, user, role(roleName)));
    }

    private Product product(Organization organization) {
        return products.saveAndFlush(new Product(organization, null, "P" + UUID.randomUUID(), "Seeded"));
    }

    private String bearer(User user) {
        return TestTokens.bearer(jwtProperties, user);
    }

    private UUID uploadAs(User admin, Organization organization, Product product, byte[] bytes, String type)
            throws Exception {
        MvcResult result = mvc.perform(multipart(imagesPath(organization, product)).file(file(type, bytes))
                        .header(AUTH, bearer(admin)))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        String[] parts = location.split("/");
        return UUID.fromString(parts[parts.length - 2]);
    }

    @Nested
    class Upload {

        @Test
        void anAdminUploadsAnImageAndGetsItsLocationAndMetadata() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme);

            mvc.perform(multipart(imagesPath(acme, shoes)).file(file("image/png", ProductImageSamples.PNG))
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString(imagesPath(acme, shoes))))
                    .andExpect(jsonPath("$.contentType").value("image/png"))
                    .andExpect(jsonPath("$.sizeBytes").value(ProductImageSamples.PNG.length))
                    .andExpect(jsonPath("$.position").value(0))
                    .andExpect(jsonPath("$.contentPath").value(org.hamcrest.Matchers.startsWith(imagesPath(acme, shoes))))
                    .andExpect(jsonPath("$.objectKey").doesNotExist());
        }

        @Test
        void anAgentCannotUpload() throws Exception {
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, agent, Roles.AGENT);
            Product shoes = product(acme);

            mvc.perform(multipart(imagesPath(acme, shoes)).file(file("image/png", ProductImageSamples.PNG))
                            .header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAnonymousCallerIsUnauthorized() throws Exception {
            Organization acme = organization("Acme");
            Product shoes = product(acme);

            mvc.perform(multipart(imagesPath(acme, shoes)).file(file("image/png", ProductImageSamples.PNG)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void anotherTenantsProductIsA404() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, admin, Roles.ADMIN);
            Product foreign = product(globex);

            mvc.perform(multipart("/api/organizations/" + acme.getId() + "/products/" + foreign.getId() + "/images")
                            .file(file("image/png", ProductImageSamples.PNG)).header(AUTH, bearer(admin)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void aFileOverTheSizeLimitIs413() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme);

            mvc.perform(multipart(imagesPath(acme, shoes)).file(file("image/png", new byte[1001]))
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isContentTooLarge())
                    .andExpect(jsonPath("$.status").value(413));
        }

        @Test
        void aDisallowedTypeIs415() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme);

            mvc.perform(multipart(imagesPath(acme, shoes)).file(file("image/gif", "GIF89a".getBytes()))
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        void htmlDisguisedAsAPngIs415() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme);

            mvc.perform(multipart(imagesPath(acme, shoes)).file(file("image/png", ProductImageSamples.HTML))
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        void aMissingFilePartIs400() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme);

            mvc.perform(multipart(imagesPath(acme, shoes)).header(AUTH, bearer(admin)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aThirdImageOnAProductLimitedToTwoIs422() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme);
            uploadAs(admin, acme, shoes, ProductImageSamples.PNG, "image/png");
            uploadAs(admin, acme, shoes, ProductImageSamples.JPEG, "image/jpeg");

            mvc.perform(multipart(imagesPath(acme, shoes)).file(file("image/webp", ProductImageSamples.WEBP))
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    class ListAndContent {

        @Test
        void aMemberListsTheImagesInUploadOrder() throws Exception {
            User admin = newUser();
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            join(acme, agent, Roles.AGENT);
            Product shoes = product(acme);
            uploadAs(admin, acme, shoes, ProductImageSamples.PNG, "image/png");
            uploadAs(admin, acme, shoes, ProductImageSamples.JPEG, "image/jpeg");

            mvc.perform(get(imagesPath(acme, shoes)).header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].position").value(0))
                    .andExpect(jsonPath("$[1].contentType").value("image/jpeg"));
        }

        @Test
        void aNonMemberGetsA404ForTheList() throws Exception {
            User outsider = newUser();
            Organization acme = organization("Acme");
            Product shoes = product(acme);

            mvc.perform(get(imagesPath(acme, shoes)).header(AUTH, bearer(outsider)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anAnonymousCallerIsUnauthorizedForTheList() throws Exception {
            Organization acme = organization("Acme");
            Product shoes = product(acme);

            mvc.perform(get(imagesPath(acme, shoes))).andExpect(status().isUnauthorized());
        }

        @Test
        void aMemberStreamsTheBytesWithSafeHeaders() throws Exception {
            User admin = newUser();
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            join(acme, agent, Roles.AGENT);
            Product shoes = product(acme);
            UUID imageId = uploadAs(admin, acme, shoes, ProductImageSamples.PNG, "image/png");

            byte[] body = mvc.perform(get(imagesPath(acme, shoes) + "/" + imageId + "/content")
                            .header(AUTH, bearer(agent)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/png"))
                    .andExpect(header().string("Content-Disposition", "inline"))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andReturn().getResponse().getContentAsByteArray();

            assertEquals(ProductImageSamples.PNG.length, body.length);
        }

        @Test
        void anotherTenantsImageContentIsA404() throws Exception {
            User admin = newUser();
            User other = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, admin, Roles.ADMIN);
            join(globex, other, Roles.ADMIN);
            Product shoes = product(acme);
            UUID imageId = uploadAs(admin, acme, shoes, ProductImageSamples.PNG, "image/png");

            mvc.perform(get(imagesPath(acme, shoes) + "/" + imageId + "/content").header(AUTH, bearer(other)))
                    .andExpect(status().isNotFound());
            Product foreign = product(globex);
            mvc.perform(get(imagesPath(globex, foreign) + "/" + imageId + "/content").header(AUTH, bearer(other)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class DeleteImage {

        @Test
        void anAdminDeletesAnImageAndItsBytesAreGone() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme);
            UUID imageId = uploadAs(admin, acme, shoes, ProductImageSamples.PNG, "image/png");
            String key = images.findObjectKeys(shoes.getId(), acme.getId()).get(0);

            mvc.perform(delete(imagesPath(acme, shoes) + "/" + imageId).header(AUTH, bearer(admin)))
                    .andExpect(status().isNoContent());

            mvc.perform(get(imagesPath(acme, shoes) + "/" + imageId + "/content").header(AUTH, bearer(admin)))
                    .andExpect(status().isNotFound());
            assertEquals(true, storage.read(acme.getId(), key).isEmpty());
        }

        @Test
        void anAgentCannotDelete() throws Exception {
            User admin = newUser();
            User agent = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            join(acme, agent, Roles.AGENT);
            Product shoes = product(acme);
            UUID imageId = uploadAs(admin, acme, shoes, ProductImageSamples.PNG, "image/png");

            mvc.perform(delete(imagesPath(acme, shoes) + "/" + imageId).header(AUTH, bearer(agent)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anotherTenantsAdminGetsA404() throws Exception {
            User admin = newUser();
            User other = newUser();
            Organization acme = organization("Acme");
            Organization globex = organization("Globex");
            join(acme, admin, Roles.ADMIN);
            join(globex, other, Roles.ADMIN);
            Product shoes = product(acme);
            UUID imageId = uploadAs(admin, acme, shoes, ProductImageSamples.PNG, "image/png");

            mvc.perform(delete(imagesPath(acme, shoes) + "/" + imageId).header(AUTH, bearer(other)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deletingTheProductRemovesItsImageObjects() throws Exception {
            User admin = newUser();
            Organization acme = organization("Acme");
            join(acme, admin, Roles.ADMIN);
            Product shoes = product(acme);
            uploadAs(admin, acme, shoes, ProductImageSamples.PNG, "image/png");
            String key = images.findObjectKeys(shoes.getId(), acme.getId()).get(0);

            mvc.perform(delete("/api/organizations/" + acme.getId() + "/products/" + shoes.getId())
                            .header(AUTH, bearer(admin)))
                    .andExpect(status().isNoContent());

            assertEquals(0, images.findObjectKeys(shoes.getId(), acme.getId()).size());
            assertEquals(true, storage.read(acme.getId(), key).isEmpty());
        }
    }
}
