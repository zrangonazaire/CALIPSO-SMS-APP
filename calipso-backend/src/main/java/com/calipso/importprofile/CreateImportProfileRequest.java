package com.calipso.importprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateImportProfileRequest(
        @NotNull Long companyId,
        @NotBlank String name,
        String description
) {
}