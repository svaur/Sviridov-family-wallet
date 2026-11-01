package ru.sviridov.wallet.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppUser;
import ru.sviridov.wallet.service.LoginRateLimiter;
import ru.sviridov.wallet.service.UserService;
import ru.sviridov.wallet.web.dto.LoginRequest;
import ru.sviridov.wallet.web.dto.UserResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final LoginRateLimiter rateLimiter;
    private final UserService userService;

    public AuthController(@Qualifier("userAuthenticationManager") AuthenticationManager authenticationManager,
                          LoginRateLimiter rateLimiter, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.userService = userService;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String key = request.username() + "|" + ClientIp.of(httpRequest);
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
            // The principal carries no display data (it is snapshotted into the
            // session), so the current name is read once here for the response.
            AppUser user = (AppUser) authentication.getPrincipal();
            return userService.get(user.getId());
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
            // Remove only the wallet context — the admin context (same cookie,
            // different attribute) must survive a wallet logout.
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
    }
}
