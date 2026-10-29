package com.calipso.auth;

import com.calipso.companyuser.CompanyUser;
import com.calipso.companyuser.CompanyUserRole;

public record AuthUserResponse(
        Long id,
        Long companyId,
        String companyName,
        String fullName,
        String username,
        String email,
        CompanyUserRole role,
        boolean platformAdmin
) {
    public static AuthUserResponse from(CompanyUser user, boolean platformAdmin) {
        return new AuthUserResponse(
                user.getId(),
                user.getCompany().getId(),
                user.getCompany().getName(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                platformAdmin
        );
    }
}
