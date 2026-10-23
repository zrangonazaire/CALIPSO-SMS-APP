package com.calipso.auth;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String identifier,
        @NotBlank String code,
        @NotBlank String newPassword
) {
}
