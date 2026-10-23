package com.calipso.smstemplate;

import jakarta.validation.constraints.NotBlank;

public record CreateSmsTemplateRequest(
        Long companyId,
        Long profileId,
        @NotBlank String name,
        @NotBlank String content
) {
}
