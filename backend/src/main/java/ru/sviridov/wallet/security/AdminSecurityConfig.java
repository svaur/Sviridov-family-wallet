package ru.sviridov.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import ru.sviridov.wallet.web.ApiError;

/**
 * Security for the admin panel ({@code /admin/**} + {@code /api/admin/**}).
 *
 * <p>Two things keep it independent from the wallet: its own
 * {@link AuthenticationManager} backed by the {@code admins} table, and its own
 * {@link HttpSessionSecurityContextRepository} attribute key. Both sessions live
 * in the same JSESSIONID cookie but never clobber each other — logging into the
 * admin does not sign the wallet out and vice versa.</p>
 */
@Configuration
public class AdminSecurityConfig {

    /** Separate session attribute; the wallet uses Spring Security's default key. */
    public static final String ADMIN_SECURITY_CONTEXT_KEY = "ADMIN_SECURITY_CONTEXT_KEY";

    private final ObjectMapper objectMapper;

    public AdminSecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public AuthenticationManager adminAuthenticationManager(AdminDetailsService adminDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsService(adminDetailsService);
        return new ProviderManager(provider);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("adminAuthenticationManager") AuthenticationManager authenticationManager) throws Exception {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setSpringSecurityContextKey(ADMIN_SECURITY_CONTEXT_KEY);
        http
                .authenticationManager(authenticationManager)
                .securityMatcher("/admin/**", "/api/admin/**")
                .securityContext(sc -> sc.securityContextRepository(repository))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/auth/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), ApiError.of(401, "Unauthorized",
                                    "Требуется вход администратора", request.getRequestURI()));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), ApiError.of(403, "Forbidden",
                                    "Доступ запрещён", request.getRequestURI()));
                        }))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
