package com.calipso.sms;

import com.calipso.campaign.CampaignChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ManualSmsRequest(
        @NotNull Long companyId,
        CampaignChannel channel,
        String subject,
        @NotBlank String message,
        List<String> phoneNumbers,
        String mediaUrl,
        String ctaLabel,
        String ctaUrl
) {
}
