package com.calipso.auth;

import com.calipso.campaign.CampaignChannel;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank String identifier,
        CampaignChannel channel
) {
}
