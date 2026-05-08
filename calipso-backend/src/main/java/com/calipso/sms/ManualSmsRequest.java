package com.calipso.sms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ManualSmsRequest(
        @NotNull Long companyId,
        @NotBlank String message,
        List<String> phoneNumbers
) {
}
