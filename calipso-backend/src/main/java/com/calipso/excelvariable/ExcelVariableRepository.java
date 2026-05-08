package com.calipso.excelvariable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExcelVariableRepository extends JpaRepository<ExcelVariable, Long> {

    List<ExcelVariable> findByProfileIdAndActiveTrue(Long profileId);

    Optional<ExcelVariable> findByProfileIdAndCode(Long profileId, String code);

    List<ExcelVariable> findByProfileIdAndPhoneTrueAndActiveTrue(Long profileId);
}