package ru.sviridov.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.sviridov.wallet.web.ApiError;

import java.io.IOException;

/**
 * After an admin resets a password the user is forced to change it: every
 * wallet call is rejected (403) until the new password is set. Only reading
 * {@code /api/me}, the change itself and logout stay available.
 *
 * <p>The flag is a snapshot in {@link AppUser}, so it needs no extra DB read;
 * a password change both clears the flag and bumps the epoch, which ends the
 * current session anyway, so the next login carries the fresh value.</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public MustChangePasswordFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/admin/")) {
            return true;
        }
        if ("/api/auth/login".equals(path) || "/api/auth/logout".equals(path)) {
            return true;
        }
        // The forced change needs to read who is signed in and then change it.
        if ("/api/me".equals(path) && "GET".equals(request.getMethod())) {
            return true;
        }
        return "/api/me/password".equals(path) && "PUT".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AppUser principal
                && principal.isMustChangePassword()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiError.of(403, "Forbidden",
                    "Требуется смена пароля", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }
}
