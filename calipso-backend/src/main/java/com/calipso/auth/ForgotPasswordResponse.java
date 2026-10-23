package com.calipso.auth;

import com.calipso.campaign.CampaignChannel;

public record ForgotPasswordResponse(
        String message,
        String maskedDestination,
        CampaignChannel channel,
        String devCodeHint
) {
}
