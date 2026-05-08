package com.calipso.smstemplate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSmsTemplateRequest(
        @NotNull Long profileId,
        @NotBlank String name,
        @NotBlank String content
) {
}
