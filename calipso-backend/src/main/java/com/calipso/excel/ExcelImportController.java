package com.calipso.excel;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.calipso.campaign.Campaign;

@RestController
@RequestMapping("/api/v1/excel")
@RequiredArgsConstructor
public class ExcelImportController {

    private final ExcelImportService excelImportService;

    @PostMapping("/campaign/{campaignId}/import")
    public Campaign importExcel(
            @PathVariable Long campaignId,
            @RequestParam("file") MultipartFile file
    ) {
        return excelImportService.importExcel(campaignId, file);
    }
}