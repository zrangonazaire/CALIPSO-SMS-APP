package com.calipso.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCampaignRequest(
        @NotNull Long profileId,
        @NotNull Long templateId,
        @NotNull Long phoneVariableId,
        @NotBlank String name,
        String description
) {
}