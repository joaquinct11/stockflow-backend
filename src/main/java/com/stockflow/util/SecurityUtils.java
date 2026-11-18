package com.stockflow.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    /** Returns true when the current user does NOT have the given permission code. */
    public static boolean lacksPermission(String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return true;
        String authority = "PERM_" + code;
        return auth.getAuthorities().stream()
                .noneMatch(a -> authority.equals(a.getAuthority()));
    }
}
