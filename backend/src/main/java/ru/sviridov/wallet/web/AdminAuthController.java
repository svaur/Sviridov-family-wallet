package ru.sviridov.wallet.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AdminSecurityConfig;
import ru.sviridov.wallet.security.AppAdmin;
import ru.sviridov.wallet.service.AdminService;
import ru.sviridov.wallet.service.AuditService;
import ru.sviridov.wallet.service.LoginRateLimiter;
import ru.sviridov.wallet.web.dto.AdminResponse;
import ru.sviridov.wallet.web.dto.LoginRequest;

/**
 * Admin sign-in, out and "who am I". Mirrors the wallet's {@code AuthController}
 * but stores the context under {@link AdminSecurityConfig#ADMIN_SECURITY_CONTEXT_KEY}
 * so the two sessions coexist in one cookie.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AuthenticationManager authenticationManager;
    private final LoginRateLimiter rateLimiter;
    private final AdminService adminService;
    private final AuditService auditService;

    public AdminAuthController(@Qualifier("adminAuthenticationManager") AuthenticationManager authenticationManager,
                               LoginRateLimiter rateLimiter, AdminService adminService, AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.adminService = adminService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public AdminResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String key = "admin|" + request.username() + "|" + ClientIp.of(httpRequest);
        if (rateLimiter.isBlocked(key)) {
            throw new TooManyRequestsException("Слишком много попыток входа. Попробуйте через несколько минут.");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            rateLimiter.reset(key);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(AdminSecurityConfig.ADMIN_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());
            AppAdmin admin = (AppAdmin) authentication.getPrincipal();
            adminService.recordLogin(admin.getId());
            auditService.record("admin", admin.getId(), "login", admin.getUsername(), admin.getId(),
                    ClientIp.of(httpRequest));
            return adminService.get(admin.getId());
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(key);
            throw e;
        }
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            // Remove only the admin context — the wallet session (same cookie,
            // different attribute) must survive an admin logout.
            session.removeAttribute(AdminSecurityConfig.ADMIN_SECURITY_CONTEXT_KEY);
        }
    }

    @GetMapping("/me")
    public AdminResponse me(@AuthenticationPrincipal AppAdmin admin) {
        return adminService.get(admin.getId());
    }
}
