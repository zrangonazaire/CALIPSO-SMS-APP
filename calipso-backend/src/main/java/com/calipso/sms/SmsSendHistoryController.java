package com.calipso.sms;

import com.calipso.auth.CompanyAccess;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.calipso.statistics.StatisticsPeriod;

@RestController
@RequestMapping("/api/v1/sms-history")
@RequiredArgsConstructor
public class SmsSendHistoryController {

    private final CompanyAccess access;

    private final SmsSendHistoryRepository historyRepository;

    @GetMapping("/company/{companyId}")
    public List<SmsSendHistoryResponse> findByCompany(@PathVariable Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        access.requireCompany(companyId);
        var period = StatisticsPeriod.of(startDate, endDate);
        return (startDate == null && endDate == null ? historyRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                : historyRepository.findForPeriod(companyId, period))
                .stream()
                .map(SmsSendHistoryResponse::from)
                .toList();
    }
}
