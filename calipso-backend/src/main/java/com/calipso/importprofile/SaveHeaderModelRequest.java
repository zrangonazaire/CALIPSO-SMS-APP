package com.calipso.importprofile;

import com.calipso.config.DataType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record SaveHeaderModelRequest(@NotNull Long companyId, @NotBlank String name,
                                     String description, @NotEmpty List<@Valid Header> columns) {
    public record Header(Long id, @NotBlank String code, @NotBlank String headerName,
                         @NotBlank String label, @NotNull DataType dataType,
                         boolean required, boolean phone) {}
}
