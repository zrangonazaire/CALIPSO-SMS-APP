package com.calipso.importprofile;

import com.calipso.auth.CompanyAccess;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/import-profiles")
@RequiredArgsConstructor
public class ExcelImportProfileController {

    private final CompanyAccess access;

    private final ExcelImportProfileRepository profileRepository;
    private final CompanyRepository companyRepository;
    private final HeaderModelService headerModels;

    @PostMapping("/header-models")
    public ExcelImportProfile createModel(@RequestBody @Valid SaveHeaderModelRequest request) {
        access.requireCompany(request.companyId());
        return headerModels.save(null, request);
    }

    @PutMapping("/{id}/header-model")
    public ExcelImportProfile updateModel(@PathVariable Long id, @RequestBody @Valid SaveHeaderModelRequest request) {
        access.requireProfile(id);
        access.requireCompany(request.companyId());
        return headerModels.save(id, request);
    }

    @PostMapping("/{id}/duplicate")
    public ExcelImportProfile duplicate(@PathVariable Long id) {
        access.requireProfile(id);
        return headerModels.duplicate(id);
    }

    @DeleteMapping("/{id}")
    public void archive(@PathVariable Long id) {
        access.requireProfile(id);
        headerModels.archive(id);
    }

    @GetMapping("/{id}/sample-excel")
    public org.springframework.http.ResponseEntity<byte[]> download(@PathVariable Long id) {
        access.requireProfile(id);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"modele-entetes.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(headerModels.download(id));
    }

    @PostMapping
    public ExcelImportProfile create(@RequestBody @Valid CreateImportProfileRequest request) {
        access.requireCompany(request.companyId());
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        ExcelImportProfile profile = ExcelImportProfile.builder()
                .company(company)
                .name(request.name())
                .description(request.description())
                .active(true)
                .build();

        return profileRepository.save(profile);
    }

    @GetMapping("/company/{companyId}")
    public List<ExcelImportProfile> findByCompany(@PathVariable Long companyId) {
        access.requireCompany(companyId);
        return profileRepository.findByCompanyIdAndActiveTrue(companyId);
    }
}
