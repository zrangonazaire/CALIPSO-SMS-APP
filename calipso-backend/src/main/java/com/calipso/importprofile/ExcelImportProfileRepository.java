package com.calipso.importprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExcelImportProfileRepository extends JpaRepository<ExcelImportProfile, Long> {

    List<ExcelImportProfile> findByCompanyIdAndActiveTrue(Long companyId);
}