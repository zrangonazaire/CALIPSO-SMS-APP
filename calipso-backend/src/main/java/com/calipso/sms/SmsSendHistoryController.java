package com.calipso.sms;

import com.calipso.auth.CompanyAccess;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sms-history")
@RequiredArgsConstructor
public class SmsSendHistoryController {

    private final CompanyAccess access;

    private final SmsSendHistoryRepository historyRepository;

    @GetMapping("/company/{companyId}")
    public List<SmsSendHistoryResponse> findByCompany(@PathVariable Long companyId) {
        access.requireCompany(companyId);
        return historyRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(SmsSendHistoryResponse::from)
                .toList();
    }
}
