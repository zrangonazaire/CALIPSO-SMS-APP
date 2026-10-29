package com.calipso.sms;

import com.calipso.auth.CompanyAccess;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/sms-api")
@RequiredArgsConstructor
public class SmsApiController {

    private final CompanyAccess access;

    private final SmsApiDashboardService dashboardService;

    @GetMapping("/company/{companyId}/dashboard")
    public SmsApiDashboardResponse getDashboard(@PathVariable Long companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        access.requireCompany(companyId);
        return dashboardService.getDashboard(companyId, startDate, endDate);
    }
}
