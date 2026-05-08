package com.calipso.excelvariable;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.calipso.importprofile.ExcelImportProfile;
import com.calipso.importprofile.ExcelImportProfileRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/excel-variables")
@RequiredArgsConstructor
public class ExcelVariableController {

    private final ExcelVariableRepository variableRepository;
    private final ExcelImportProfileRepository profileRepository;

    @PostMapping
    public ExcelVariable create(@RequestBody @Valid CreateExcelVariableRequest request) {
        ExcelImportProfile profile = profileRepository.findById(request.profileId())
                .orElseThrow(() -> new RuntimeException("Profil d'import introuvable"));

        String code = normalizeCode(request.code());

        variableRepository.findByProfileIdAndCode(profile.getId(), code)
                .ifPresent(existing -> {
                    throw new RuntimeException("Cette variable existe déjà pour ce profil");
                });

        boolean phone = Boolean.TRUE.equals(request.phone());

        ExcelVariable variable = ExcelVariable.builder()
                .company(profile.getCompany())
                .profile(profile)
                .code(code)
                .label(request.label())
                .dataType(request.dataType())
                .required(phone || Boolean.TRUE.equals(request.required()))
                .phone(phone)
                .active(true)
                .build();

        return variableRepository.save(variable);
    }

    @GetMapping("/profile/{profileId}")
    public List<ExcelVariable> findByProfile(@PathVariable Long profileId) {
        return variableRepository.findByProfileIdAndActiveTrue(profileId);
    }

    private String normalizeCode(String code) {
        return code.trim()
                .toUpperCase()
                .replace(" ", "_")
                .replace("-", "_");
    }
}
