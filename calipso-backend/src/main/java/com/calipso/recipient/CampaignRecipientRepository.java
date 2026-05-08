package com.calipso.recipient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {

    List<CampaignRecipient> findByCampaignId(Long campaignId);

    long countByCampaignId(Long campaignId);
}
