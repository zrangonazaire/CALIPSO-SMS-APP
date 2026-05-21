package com.calipso.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
