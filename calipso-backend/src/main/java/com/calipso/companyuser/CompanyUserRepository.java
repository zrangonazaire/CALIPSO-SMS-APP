package com.calipso.companyuser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyUserRepository extends JpaRepository<CompanyUser, Long> {
    List<CompanyUser> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    boolean existsByUsernameIgnoreCase(String username);
}
