package com.connecta.backend.support;

import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.org.repository.OrganizationRepository;
import com.connecta.backend.user.repository.RoleRepository;
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
        OrganizationRepository.class, MembershipRepository.class})
public @interface MockRepositories {
}
