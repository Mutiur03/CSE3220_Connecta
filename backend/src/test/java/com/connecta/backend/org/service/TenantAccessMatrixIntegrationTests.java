package com.connecta.backend.org.service;

import com.connecta.backend.support.SharedPostgres;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.connecta.backend.config.JwtProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.org.domain.Invitation;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.InvitationRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.product.domain.ProductImage;
import com.connecta.backend.product.domain.StockMovement;
import com.connecta.backend.product.domain.StockMovementType;
import com.connecta.backend.product.repository.ProductCategoryRepository;
import com.connecta.backend.product.repository.ProductImageRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.product.repository.StockMovementRepository;
import com.connecta.backend.product.service.ProductImageSamples;
import com.connecta.backend.product.service.ProductImageStorage;
import com.connecta.backend.support.InboxFixtures;
import com.connecta.backend.support.RandomJwtSecret;
import com.connecta.backend.support.StubChannelClients;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestTokens;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The tenant-isolation and role matrix, one row per endpoint under {@code /api/organizations} and
 * {@code /api/admin}, against real PostgreSQL with only Meta stubbed. For every row:
 * <ol>
 *   <li>no token is 401;</li>
 *   <li>a member of ANOTHER organization is 404, never 403 and never data, both under the other organization's
 *       id and under its own id with this organization's resource ids;</li>
 *   <li>an ADMIN row is 403 for an AGENT of the organization (with an AGENT global role and with an ADMIN one),
 *       and the organization's ADMIN gets through;</li>
 *   <li>a MEMBER row lets an AGENT through;</li>
 *   <li>an OWNER row (system-wide {@code /api/admin}) is 403 for an ADMIN and an AGENT.</li>
 * </ol>
 * The last test fails when a mapped endpoint has no row, so a new endpoint cannot ship without a rule here.
 * Pattern: table-driven test (a declarative row per endpoint driving one generic check).
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@Import({TestClock.class, StubChannelClients.class})
@ContextConfiguration(initializers = RandomJwtSecret.class)
class TenantAccessMatrixIntegrationTests extends SharedPostgres {

    /** Who may call an endpoint. GLOBAL is signed-in only and not tied to one organization. */
    enum Access { MEMBER, ADMIN, GLOBAL, OWNER }

    /** Success means any answer other than 401, 403 or 404, for endpoints that depend on outside systems. */
    private static final int PASSES = 0;
    private static final Object FILE = new Object();
    private static final String PAGE_BODY = "{\"pageIds\":[\"1\"]}";

    /** One endpoint: method, path with placeholders, role, the status a permitted caller gets, request body. */
    record Row(HttpMethod method, String path, Access access, int ok, Object body) {
        @Override
        public String toString() {
            return method + " " + path + " (" + access + ")";
        }

        boolean hasResource() {
            return Pattern.compile("\\{(?!org\\})[a-z]+\\}").matcher(path).find();
        }
    }

    private static Row row(HttpMethod method, String path, Access access, int ok, Object body) {
        return new Row(method, path, access, ok, body);
    }

    private static Row row(HttpMethod method, String path, Access access, int ok) {
        return row(method, path, access, ok, null);
    }

    private static final String ORG = "/api/organizations/{org}";
    private static final String CONV = ORG + "/conversations/{conv}";
    private static final String PRODUCT = ORG + "/products/{product}";

    static final List<Row> ROWS = List.of(
            // Organizations
            row(HttpMethod.POST, "/api/organizations", Access.GLOBAL, 201,
                    "{\"name\":\"Fresh\",\"slug\":\"fresh-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"),
            row(HttpMethod.GET, "/api/organizations", Access.GLOBAL, 200),
            row(HttpMethod.GET, ORG, Access.MEMBER, 200),
            row(HttpMethod.PATCH, ORG, Access.ADMIN, 200, "{\"name\":\"Renamed\"}"),
            row(HttpMethod.DELETE, ORG, Access.ADMIN, 204),
            // Members, invitations, availability
            row(HttpMethod.GET, ORG + "/members", Access.MEMBER, 200),
            row(HttpMethod.PATCH, ORG + "/members/{member}", Access.ADMIN, 200, "{\"role\":\"AGENT\"}"),
            row(HttpMethod.DELETE, ORG + "/members/{member}", Access.ADMIN, 204),
            row(HttpMethod.POST, ORG + "/invitations", Access.ADMIN, 201,
                    "{\"email\":\"new@example.com\",\"role\":\"AGENT\"}"),
            row(HttpMethod.GET, ORG + "/invitations", Access.ADMIN, 200),
            row(HttpMethod.DELETE, ORG + "/invitations/{invitation}", Access.ADMIN, 204),
            row(HttpMethod.GET, ORG + "/availability", Access.ADMIN, 200),
            row(HttpMethod.PUT, ORG + "/members/{member}/assignable", Access.ADMIN, 200, "{\"assignable\":true}"),
            row(HttpMethod.GET, ORG + "/availability/report?from=2026-11-01&to=2026-11-07", Access.ADMIN, 200),
            // Realtime
            row(HttpMethod.POST, ORG + "/realtime/tickets", Access.MEMBER, 201),
            // Conversations
            row(HttpMethod.GET, ORG + "/conversations", Access.MEMBER, 200),
            row(HttpMethod.GET, CONV, Access.MEMBER, 200),
            row(HttpMethod.GET, CONV + "/messages", Access.MEMBER, 200),
            row(HttpMethod.PATCH, CONV, Access.MEMBER, 200, "{\"status\":\"CLOSED\"}"),
            row(HttpMethod.PUT, CONV + "/read-state", Access.MEMBER, 204, "{\"unread\":false}"),
            row(HttpMethod.POST, CONV + "/messages", Access.MEMBER, 201, "{\"text\":\"hi\"}"),
            row(HttpMethod.POST, CONV + "/messages/attachments", Access.MEMBER, PASSES, FILE),
            row(HttpMethod.POST, CONV + "/assignment", Access.ADMIN, 200, "{\"membershipId\":\"{member}\"}"),
            row(HttpMethod.GET, CONV + "/assignments", Access.ADMIN, 200),
            row(HttpMethod.GET, CONV + "/product-search", Access.MEMBER, 200),
            row(HttpMethod.GET, CONV + "/product-shares/preview?productId={product}", Access.MEMBER, 200),
            row(HttpMethod.POST, CONV + "/product-shares", Access.MEMBER, PASSES,
                    "{\"productId\":\"{product}\",\"text\":\"look\"}"),
            // Products, categories, images, stock
            row(HttpMethod.GET, ORG + "/products", Access.MEMBER, 200),
            row(HttpMethod.POST, ORG + "/products", Access.ADMIN, 201, "{\"name\":\"Mug\"}"),
            row(HttpMethod.GET, PRODUCT, Access.MEMBER, 200),
            row(HttpMethod.PUT, PRODUCT, Access.ADMIN, 200, "{\"name\":\"Cup\"}"),
            row(HttpMethod.DELETE, PRODUCT, Access.ADMIN, 204),
            row(HttpMethod.POST, PRODUCT + "/images", Access.ADMIN, 201, FILE),
            row(HttpMethod.GET, PRODUCT + "/images", Access.MEMBER, 200),
            row(HttpMethod.GET, PRODUCT + "/images/{image}/content", Access.MEMBER, 200),
            row(HttpMethod.DELETE, PRODUCT + "/images/{image}", Access.ADMIN, 204),
            row(HttpMethod.POST, PRODUCT + "/stock-movements", Access.MEMBER, 201,
                    "{\"type\":\"RECEIVE\",\"quantity\":2}"),
            row(HttpMethod.GET, PRODUCT + "/stock-movements", Access.MEMBER, 200),
            row(HttpMethod.GET, ORG + "/stock-movements", Access.MEMBER, 200),
            row(HttpMethod.GET, ORG + "/stock-movements/{movement}", Access.MEMBER, 200),
            row(HttpMethod.GET, ORG + "/inventory/summary", Access.MEMBER, 200),
            row(HttpMethod.GET, ORG + "/product-categories", Access.MEMBER, 200),
            row(HttpMethod.POST, ORG + "/product-categories", Access.ADMIN, 201, "{\"name\":\"Fresh\"}"),
            row(HttpMethod.GET, ORG + "/product-categories/{category}", Access.MEMBER, 200),
            row(HttpMethod.PUT, ORG + "/product-categories/{category}", Access.ADMIN, 200, "{\"name\":\"Home\"}"),
            row(HttpMethod.DELETE, ORG + "/product-categories/{category}", Access.ADMIN, 204),
            // Meta connection (ADMIN; Meta is not configured here, so an admin gets past the checks)
            row(HttpMethod.POST, ORG + "/meta/authorization", Access.ADMIN, PASSES),
            row(HttpMethod.GET, ORG + "/meta/authorization", Access.ADMIN, PASSES),
            row(HttpMethod.POST, ORG + "/meta/instagram", Access.ADMIN, PASSES),
            row(HttpMethod.GET, ORG + "/meta/instagram", Access.ADMIN, PASSES),
            row(HttpMethod.GET, ORG + "/meta/pages", Access.ADMIN, PASSES),
            row(HttpMethod.GET, ORG + "/meta/pages/available", Access.ADMIN, PASSES),
            row(HttpMethod.POST, ORG + "/meta/pages", Access.ADMIN, PASSES, PAGE_BODY),
            // System owner only
            row(HttpMethod.GET, "/api/admin/organizations", Access.OWNER, 200),
            row(HttpMethod.GET, "/api/admin/users", Access.OWNER, 200),
            row(HttpMethod.PATCH, "/api/admin/users/{user}", Access.OWNER, 200, "{\"enabled\":true}"));

    static Stream<Row> rows() {
        return ROWS.stream();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    StubChannelClients meta;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    ParticipantRepository participants;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ConversationParticipantRepository links;

    @Autowired
    MessageRepository messages;

    @Autowired
    ProductCategoryRepository categories;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductImageRepository images;

    @Autowired
    StockMovementRepository movements;

    @Autowired
    InvitationRepository invitations;

    @Autowired
    ProductImageStorage storage;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private Organization orgA;
    private Organization orgB;
    private User owner;
    private User admin;
    private User agentGlobal;
    private User agentOrgOnly;
    private User foreignAdmin;
    private User foreignAgent;
    private String conv;
    private String product;
    private String category;
    private String image;
    private String movement;
    private String invitation;
    private String member;

    @BeforeEach
    void twoTenants() {
        meta.reset();
        InboxFixtures fixtures = new InboxFixtures(users, roles, organizations, memberships, participants,
                conversations, links, messages);
        orgA = fixtures.organization("Alpha");
        orgB = fixtures.organization("Beta");
        owner = fixtures.user(Roles.OWNER);
        admin = fixtures.user(Roles.ADMIN);
        agentGlobal = fixtures.user(Roles.AGENT);
        agentOrgOnly = fixtures.user(Roles.ADMIN);
        foreignAdmin = fixtures.user(Roles.ADMIN);
        foreignAgent = fixtures.user(Roles.ADMIN);
        fixtures.join(orgA, admin, Roles.ADMIN);
        fixtures.join(orgA, agentGlobal, Roles.AGENT);
        fixtures.join(orgA, agentOrgOnly, Roles.AGENT);
        User colleague = fixtures.user(Roles.AGENT); // the member the rows act on: leaving is allowed to oneself
        fixtures.join(orgA, colleague, Roles.AGENT);
        fixtures.join(orgB, foreignAdmin, Roles.ADMIN);
        fixtures.join(orgB, foreignAgent, Roles.AGENT);

        Conversation conversation = fixtures.conversation(orgA, Channel.MESSENGER, "Ann", "Secret", TestClock.NOW);
        ProductCategory kitchen = categories.saveAndFlush(new ProductCategory(orgA, "Kitchen", "Pans"));
        Product pan = products.saveAndFlush(new Product(orgA, kitchen, "Pan", "Cast iron"));
        String key = storage.store(orgA.getId(), pan.getId(), "image/png", ProductImageSamples.PNG);
        ProductImage stored = images.saveAndFlush(
                new ProductImage(pan, key, "image/png", ProductImageSamples.PNG.length, 1));
        StockMovement received = movements.saveAndFlush(
                new StockMovement(pan, StockMovementType.RECEIVE, 5, 5, null, admin));
        Invitation pending = invitations.saveAndFlush(new Invitation(orgA, "pending@example.com",
                roles.findByName(Roles.AGENT).orElseThrow(), admin, "hash-" + UUID.randomUUID(), TestClock.NOW,
                TestClock.NOW.plus(Duration.ofDays(7))));
        Membership agentMembership = memberships.findByOrganizationIdAndUserId(orgA.getId(), colleague.getId())
                .orElseThrow();

        conv = conversation.getId().toString();
        product = pan.getId().toString();
        category = kitchen.getId().toString();
        image = stored.getId().toString();
        movement = received.getId().toString();
        invitation = pending.getId().toString();
        member = agentMembership.getId().toString();
    }

    private String url(Row row, Organization organization) {
        return row.path().replace("{org}", organization.getId().toString()).replace("{conv}", conv)
                .replace("{product}", product).replace("{category}", category).replace("{image}", image)
                .replace("{movement}", movement).replace("{invitation}", invitation).replace("{member}", member)
                .replace("{user}", agentGlobal.getId().toString());
    }

    private int call(Row row, Organization organization, User caller) throws Exception {
        String url = url(row, organization);
        AbstractMockHttpServletRequestBuilder<?> request;
        if (row.body() == FILE) {
            request = multipart(url).file(new MockMultipartFile("file", "a.png", "image/png",
                    ProductImageSamples.PNG));
        } else {
            request = request(row.method(), url);
            if (row.body() != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(url(row.body().toString(), organization));
            }
        }
        if (caller != null) {
            request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(jwtProperties, caller));
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private String url(String text, Organization organization) {
        return url(new Row(null, text, null, 0, null), organization);
    }

    private static void expect(Row row, String who, int expected, int actual) {
        assertEquals(expected, actual, row + " as " + who);
    }

    private static void expectPassed(Row row, String who, int actual) {
        if (row.ok() == PASSES) {
            assertTrue(actual != 401 && actual != 403 && actual != 404 && actual < 500,
                    row + " as " + who + " was refused or failed with " + actual);
        } else {
            expect(row, who, row.ok(), actual);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void everyEndpointEnforcesItsTenantAndRoleRule(Row row) throws Exception {
        expect(row, "nobody", 401, call(row, orgA, null));
        if (row.access() == Access.OWNER) {
            ownerOnly(row);
        } else if (row.access() == Access.GLOBAL) {
            expectPassed(row, "any signed-in user", call(row, orgA, agentGlobal));
        } else {
            tenantScoped(row);
        }
    }

    private void ownerOnly(Row row) throws Exception {
        expect(row, "an ADMIN", 403, call(row, orgA, admin));
        expect(row, "an AGENT", 403, call(row, orgA, agentGlobal));
        expectPassed(row, "the OWNER", call(row, orgA, owner));
    }

    private void tenantScoped(Row row) throws Exception {
        for (User stranger : List.of(foreignAdmin, foreignAgent)) {
            expect(row, "another organization's member under its id", 404, call(row, orgA, stranger));
            if (row.hasResource() && (stranger == foreignAdmin || row.access() == Access.MEMBER)) {
                expect(row, "another organization's member under their own id", 404, call(row, orgB, stranger));
            }
        }
        if (row.access() == Access.ADMIN) {
            expect(row, "an AGENT (global AGENT)", 403, call(row, orgA, agentGlobal));
            expect(row, "an AGENT (global ADMIN)", 403, call(row, orgA, agentOrgOnly));
        } else {
            expectPassed(row, "an AGENT", call(row, orgA, agentOrgOnly));
        }
        expectPassed(row, "the ADMIN", call(row, orgA, admin));
    }

    /** Fails when an endpoint under /api/organizations or /api/admin has no row above, or a row has no endpoint. */
    @Test
    void everyMappedEndpointHasARowAndEveryRowAnEndpoint() {
        Set<String> mapped = handlerMapping.getHandlerMethods().entrySet().stream()
                // the test-only controllers in the support package are not application endpoints
                .filter(entry -> !entry.getValue().getBeanType().getPackageName().endsWith(".support"))
                .map(java.util.Map.Entry::getKey)
                .flatMap(TenantAccessMatrixIntegrationTests::endpoints)
                .filter(endpoint -> endpoint.contains(" /api/organizations") || endpoint.contains(" /api/admin"))
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> declared = ROWS.stream()
                .map(row -> row.method() + " " + normalize(row.path().replaceAll("\\?.*", "")))
                .collect(Collectors.toSet());

        Set<String> unregistered = new HashSet<>(mapped);
        unregistered.removeAll(declared);
        Set<String> stale = new HashSet<>(declared);
        stale.removeAll(mapped);
        assertTrue(unregistered.isEmpty(),
                "Endpoints without a row in TenantAccessMatrixIntegrationTests.ROWS (add one): " + unregistered);
        assertTrue(stale.isEmpty(), "Rows without an endpoint (remove or fix): " + stale);
    }

    private static Stream<String> endpoints(RequestMappingInfo info) {
        Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
        return info.getMethodsCondition().getMethods().stream()
                .flatMap(method -> patterns.stream().map(pattern -> method + " " + normalize(pattern)));
    }

    private static String normalize(String path) {
        return path.replaceAll("\\{[^}]+\\}", "{}");
    }
}
