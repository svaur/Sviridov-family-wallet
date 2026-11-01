package ru.sviridov.wallet.web;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves the client IP, honouring the first hop of a single-proxy X-Forwarded-For. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
