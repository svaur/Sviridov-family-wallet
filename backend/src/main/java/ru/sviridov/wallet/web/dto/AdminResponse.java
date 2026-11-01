package ru.sviridov.wallet.web.dto;

import ru.sviridov.wallet.domain.Admin;

/** The signed-in admin as the UI sees it. */
public record AdminResponse(Long id, String username, boolean mustChangePassword) {

    public static AdminResponse from(Admin admin) {
        return new AdminResponse(admin.getId(), admin.getUsername(), admin.isMustChangePassword());
    }
}
