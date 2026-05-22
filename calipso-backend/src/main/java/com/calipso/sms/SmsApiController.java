package com.calipso.sms;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sms-api")
@RequiredArgsConstructor
public class SmsApiController {

    private final SmsApiDashboardService dashboardService;

    @GetMapping("/company/{companyId}/dashboard")
    public SmsApiDashboardResponse getDashboard(@PathVariable Long companyId) {
        return dashboardService.getDashboard(companyId);
    }
}
