package ru.sviridov.wallet.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sviridov.wallet.security.AppAdmin;
import ru.sviridov.wallet.service.AdminUserService;
import ru.sviridov.wallet.web.dto.CreateUserRequest;
import ru.sviridov.wallet.web.dto.ResetPasswordResponse;
import ru.sviridov.wallet.web.dto.UserAdminResponse;

import java.util.List;

/**
 * Admin-side user management. Every path is under {@code /api/admin/**}, so it
 * is served by the admin security chain and requires {@code ROLE_ADMIN}. Each
 * mutation is attributed to the acting admin and recorded in the audit trail.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<UserAdminResponse> list() {
        return adminUserService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminResponse create(@Valid @RequestBody CreateUserRequest request,
                                    @AuthenticationPrincipal AppAdmin admin,
                                    HttpServletRequest httpRequest) {
        return adminUserService.create(admin.getId(), ClientIp.of(httpRequest),
                request.username(), request.password(), request.firstName(), request.lastName());
    }

    @PostMapping("/{id}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ban(@PathVariable Long id, @AuthenticationPrincipal AppAdmin admin,
                    HttpServletRequest httpRequest) {
        adminUserService.setActive(admin.getId(), ClientIp.of(httpRequest), id, false);
    }

    @PostMapping("/{id}/unban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unban(@PathVariable Long id, @AuthenticationPrincipal AppAdmin admin,
                      HttpServletRequest httpRequest) {
        adminUserService.setActive(admin.getId(), ClientIp.of(httpRequest), id, true);
    }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable Long id, @AuthenticationPrincipal AppAdmin admin,
                                               HttpServletRequest httpRequest) {
        return adminUserService.resetPassword(admin.getId(), ClientIp.of(httpRequest), id);
    }

    @PostMapping("/{id}/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forceLogout(@PathVariable Long id, @AuthenticationPrincipal AppAdmin admin,
                            HttpServletRequest httpRequest) {
        adminUserService.forceLogout(admin.getId(), ClientIp.of(httpRequest), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppAdmin admin,
                       HttpServletRequest httpRequest) {
        adminUserService.delete(admin.getId(), ClientIp.of(httpRequest), id);
    }
}
