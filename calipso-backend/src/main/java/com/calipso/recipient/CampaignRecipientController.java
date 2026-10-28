package com.calipso.recipient;

import com.calipso.auth.CompanyAccess;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recipients")
@RequiredArgsConstructor
public class CampaignRecipientController {

    private final CompanyAccess access;

    private final CampaignRecipientRepository recipientRepository;

    @GetMapping("/campaign/{campaignId}")
    public List<CampaignRecipient> findByCampaign(@PathVariable Long campaignId) {
        access.requireCampaign(campaignId);
        return recipientRepository.findByCampaignId(campaignId);
    }
}