package com.connecta.backend.support;

import com.connecta.backend.auth.repository.RefreshTokenRepository;
import com.connecta.backend.user.repository.RoleRepository;
import com.connecta.backend.user.repository.UserRepository;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Tests never start a database (JPA is excluded), so Spring Data repositories do not exist.
 * Put this on any {@code @SpringBootTest} class to replace them with mocks.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@MockitoBean(types = {UserRepository.class, RoleRepository.class, RefreshTokenRepository.class})
public @interface MockRepositories {
}
