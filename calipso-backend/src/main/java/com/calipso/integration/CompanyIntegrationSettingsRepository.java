package com.calipso.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyIntegrationSettingsRepository extends JpaRepository<CompanyIntegrationSettings, Long> {
    Optional<CompanyIntegrationSettings> findByCompanyId(Long companyId);
}
