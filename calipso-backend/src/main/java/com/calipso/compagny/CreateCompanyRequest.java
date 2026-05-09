package com.calipso.compagny;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
        @NotBlank String name,
        @Email String email,
        String phone,
        String senderPhone,
        String address,
        String contactName,
        String businessType
) {
}
