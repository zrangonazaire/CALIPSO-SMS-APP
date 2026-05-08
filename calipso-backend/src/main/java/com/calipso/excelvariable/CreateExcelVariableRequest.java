package com.calipso.excelvariable;


import com.calipso.config.DataType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExcelVariableRequest(
        @NotNull Long profileId,
        @NotBlank String code,
        @NotBlank String label,
        @NotNull DataType dataType,
        Boolean required,
        Boolean phone
) {
}
