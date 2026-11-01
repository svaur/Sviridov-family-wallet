package ru.sviridov.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.sviridov.wallet.domain.User;
import ru.sviridov.wallet.domain.UserRepository;
import ru.sviridov.wallet.web.ApiError;

import java.io.IOException;
import java.time.Instant;

/**
 * Rejects a session whose epoch is stale, which is how "sign out everywhere"
 * works after a password change, a ban/unban, an admin password reset or a
 * forced logout.
 *
 * <p>Runs after Spring Security (default order is lower than the security
 * filter chain) but still inside its request scope, so the context is loaded.
 * The cost is one primary-key lookup per authenticated request — the price of
 * invalidating sessions without a server-side registry.</p>
 *
 * <p>Only the wallet context is removed, not the whole session: the admin
 * context may share the same cookie and must survive a wallet epoch change.</p>
 *
 * <p>Login and logout are exempt: a browser that still holds the stale session
 * cookie must be able to authenticate again, and logout must be able to clear
 * the context.</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SessionEpochFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SessionEpochFilter(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || "/api/auth/login".equals(path)
                || "/api/auth/logout".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUser principal) {
            Instant current = userRepository.findById(principal.getId())
                    .map(User::getSessionEpoch)
                    .orElse(null);
            if (current == null || !current.equals(principal.getSessionEpoch())) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                }
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                objectMapper.writeValue(response.getWriter(), ApiError.of(401, "Unauthorized",
                        "Сессия завершена. Войдите снова.", request.getRequestURI()));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
