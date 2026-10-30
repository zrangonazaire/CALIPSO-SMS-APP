package com.calipso.platform;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class PlatformOverviewController {
    private final PlatformOverviewService overviewService;

    @GetMapping("/overview")
    public PlatformOverviewResponse getOverview() {
        return overviewService.getOverview();
    }
}
