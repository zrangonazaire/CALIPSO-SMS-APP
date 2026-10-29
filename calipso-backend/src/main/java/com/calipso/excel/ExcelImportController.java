package com.calipso.excel;


import lombok.RequiredArgsConstructor;
import com.calipso.auth.CompanyAccess;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.calipso.campaign.Campaign;

@RestController
@RequestMapping("/api/v1/excel")
@RequiredArgsConstructor
public class ExcelImportController {

    private final ExcelImportService excelImportService;
    private final CompanyAccess access;

    @PostMapping("/campaign/{campaignId}/import")
    public Campaign importExcel(
            @PathVariable Long campaignId,
            @RequestParam("file") MultipartFile file
    ) {
        access.requireCampaign(campaignId);
        return excelImportService.importExcel(campaignId, file);
    }
}
