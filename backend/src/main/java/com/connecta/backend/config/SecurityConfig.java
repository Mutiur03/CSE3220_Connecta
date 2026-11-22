package com.connecta.backend.config;

import com.connecta.backend.auth.controller.ProblemDetailAccessHandlers;
import com.connecta.backend.auth.service.TokenService;
import com.connecta.backend.user.domain.Roles;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless API security: every request is authenticated by its bearer access token (JWT),
 * checked by Spring's BearerTokenAuthenticationFilter using the JwtDecoder from JwtConfig.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    /** Reachable without a token. The API docs are disabled in the prod profile, so nothing is served there. */
    private static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
        "/api/setup/**",
        "/api/invitations/preview", "/api/invitations/accept-new",
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    private static final String[] PRODUCT_CATEGORY_PATHS = {
        "/api/organizations/*/product-categories", "/api/organizations/*/product-categories/*"
    };

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        ProblemDetailAccessHandlers errors = new ProblemDetailAccessHandlers();
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable()) // no cookies or sessions here, tokens are sent in a header
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // The system owner's view across every organization: OWNER only.
                        .requestMatchers("/api/admin/organizations").hasRole(Roles.OWNER)
                        // Everything under /api/admin needs ADMIN (OWNER inherits it, see roleHierarchy).
                        .requestMatchers("/api/admin/**").hasRole(Roles.ADMIN)
                        // Product categories: any signed-in member reads; writes need ADMIN (OWNER inherits).
                        .requestMatchers(HttpMethod.POST, PRODUCT_CATEGORY_PATHS).hasRole(Roles.ADMIN)
                        .requestMatchers(HttpMethod.PUT, PRODUCT_CATEGORY_PATHS).hasRole(Roles.ADMIN)
                        .requestMatchers(HttpMethod.DELETE, PRODUCT_CATEGORY_PATHS).hasRole(Roles.ADMIN)
                        // Health is public; metrics, info and the Prometheus scrape need ADMIN.
                        .requestMatchers("/actuator/**").hasRole(Roles.ADMIN)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(ignoreTokenOnPublicPaths())
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }

    /**
     * Spring rejects a request with a bad Authorization header even on a public path. A browser that still
     * holds an expired access token must be able to call login and refresh, so public paths ignore the header.
     */
    private static BearerTokenResolver ignoreTokenOnPublicPaths() {
        RequestMatcher publicPaths = new OrRequestMatcher(Arrays.stream(PUBLIC_PATHS)
                .map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(path))
                .toList());
        DefaultBearerTokenResolver defaults = new DefaultBearerTokenResolver();
        return request -> publicPaths.matches(request) ? null : defaults.resolve(request);
    }

    /**
     * OWNER inherits everything ADMIN can do, and ADMIN everything AGENT can do. Spring applies this to
     * URL rules and to @PreAuthorize / @PostAuthorize alike.
     */
    @Bean
    RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy(
                Roles.AUTHORITY_PREFIX + Roles.OWNER + " > " + Roles.AUTHORITY_PREFIX + Roles.ADMIN + "\n"
                + Roles.AUTHORITY_PREFIX + Roles.ADMIN + " > " + Roles.AUTHORITY_PREFIX + Roles.AGENT);
    }

    /** Maps the token's "roles" claim to ROLE_* authorities (ROLE_ADMIN, ROLE_AGENT, ...). */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.CLAIM_ROLES);
        authorities.setAuthorityPrefix(Roles.AUTHORITY_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Browsers may call the API only from the listed origins (comma separated).
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
