package com.calipso.compagny;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RechargeWalletRequest(
        @NotNull @Min(1) Integer smsUnits
) {
}
