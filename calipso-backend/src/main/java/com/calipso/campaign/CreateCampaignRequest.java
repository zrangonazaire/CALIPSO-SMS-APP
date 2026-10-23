package com.calipso.campaign;

import jakarta.validation.constraints.NotBlank;

public record CreateCampaignRequest(
        Long companyId,
        Long profileId,
        Long templateId,
        Long phoneVariableId,
        @NotBlank String name,
        String description,
        CampaignChannel channel,
        String messageTemplate,
        String emailSubject,
        String recipientColumn,
        String mediaUrl,
        String ctaLabel,
        String ctaUrl
) {
}
