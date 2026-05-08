package com.calipso.companyuser;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCompanyUserRequest(
        @NotNull Long companyId,
        @NotBlank String fullName,
        @NotBlank String username,
        @Email @NotBlank String email,
        String phone,
        CompanyUserRole role
) {
}
