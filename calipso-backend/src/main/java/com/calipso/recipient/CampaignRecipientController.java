package com.calipso.recipient;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recipients")
@RequiredArgsConstructor
public class CampaignRecipientController {

    private final CampaignRecipientRepository recipientRepository;

    @GetMapping("/campaign/{campaignId}")
    public List<CampaignRecipient> findByCampaign(@PathVariable Long campaignId) {
        return recipientRepository.findByCampaignId(campaignId);
    }
}