package com.connecta.backend.support;

import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.file.repository.StoredFileRepository;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.org.repository.AgentAvailabilityRepository;
import com.connecta.backend.org.repository.InvitationRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.product.repository.ProductCategoryRepository;
import com.connecta.backend.product.repository.ProductImageRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.meta.repository.MetaAuthorizationRepository;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.meta.repository.MetaProcessedMessageRepository;
import com.connecta.backend.meta.repository.MetaViewOnceMarkRepository;
import com.connecta.backend.meta.repository.MetaWebhookEventRepository;
import com.connecta.backend.product.repository.StockMovementRepository;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.setup.repository.SystemSetupRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests never start a database (JPA is excluded), so Spring Data repositories do not exist.
 * Also fixes the clock and the JWT secret. Put this on any {@code @SpringBootTest} class to replace them with mocks.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TestClock.class)
@ContextConfiguration(initializers = RandomJwtSecret.class)
@MockitoBean(types = {UserRepository.class, RoleRepository.class, RefreshTokenRepository.class,
        OrganizationRepository.class, MembershipRepository.class, InvitationRepository.class,
        SystemSetupRepository.class, ProductRepository.class, ProductCategoryRepository.class,
        StockMovementRepository.class,
        ProductImageRepository.class, MetaAuthorizationRepository.class, MetaConnectedPageRepository.class,
        MetaConnectedAccountRepository.class, MetaWebhookEventRepository.class,
        MetaProcessedMessageRepository.class, MetaViewOnceMarkRepository.class, ParticipantRepository.class, ConversationRepository.class,
        ConversationParticipantRepository.class, MessageRepository.class, MessageAttachmentRepository.class,
        StoredFileRepository.class, AgentAvailabilityRepository.class})
public @interface MockRepositories {
}
