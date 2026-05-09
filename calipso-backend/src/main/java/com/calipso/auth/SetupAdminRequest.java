package com.calipso.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SetupAdminRequest(
        @NotBlank String companyName,
        @NotBlank String fullName,
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String password
) {
}
