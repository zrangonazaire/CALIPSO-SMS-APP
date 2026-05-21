package com.calipso.subscription;

import jakarta.validation.constraints.NotNull;

public record SubscribeRequest(
        @NotNull SubscriptionPlanCode planCode
) {
}
