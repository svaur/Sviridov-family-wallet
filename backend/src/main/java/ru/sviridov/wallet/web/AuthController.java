package ru.sviridov.wallet.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.LoginRateLimiter;
import ru.sviridov.wallet.web.dto.LoginRequest;
import ru.sviridov.wallet.web.dto.UserResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthenticationManager authenticationManager, LoginRateLimiter rateLimiter) {
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String key = request.username() + "|" + clientIp(httpRequest);
        if (rateLimiter.isBlocked(key)) {
            throw new TooManyRequestsException("Слишком много попыток входа. Попробуйте через несколько минут.");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            rateLimiter.reset(key);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());
            AppUser user = (AppUser) authentication.getPrincipal();
            return new UserResponse(user.getId(), user.getUsername());
        } catch (BadCredentialsException e) {
            rateLimiter.recordFailure(key);
            throw e;
        }
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        AppUser user = (AppUser) authentication.getPrincipal();
        return new UserResponse(user.getId(), user.getUsername());
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
