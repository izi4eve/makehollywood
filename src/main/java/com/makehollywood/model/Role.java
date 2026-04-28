package com.makehollywood.model;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
    REGISTERED,
    MODERATOR,
    ADMIN,
    BLOCKED;

    @Override
    public String getAuthority() {
        return "ROLE_" + name(); // Use the "ROLE_" prefix to comply with Spring Security requirements
    }
}