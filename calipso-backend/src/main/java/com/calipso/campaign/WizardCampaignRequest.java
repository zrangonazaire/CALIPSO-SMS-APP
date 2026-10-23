package com.calipso.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record WizardCampaignRequest(
        @NotNull Long companyId,
        @NotBlank String name,
        String description,
        CampaignChannel channel,
        @NotBlank String messageTemplate,
        String emailSubject,
        String recipientColumn,
        String mediaUrl,
        String ctaLabel,
        String ctaUrl,
        List<Map<String, Object>> rows,
        boolean sendImmediately
) {
}
