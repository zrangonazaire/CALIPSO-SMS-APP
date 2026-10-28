package com.calipso.excel;

import com.calipso.auth.CompanyAccess;

import com.calipso.campaign.*;
import com.calipso.compagny.Company;
import com.calipso.recipient.CampaignRecipientRepository;
import com.calipso.sms.MultichannelGatewayService;
import com.calipso.sms.SmsMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcelImportServiceTest {
    private final ExcelTableReader reader = mock(ExcelTableReader.class);
    private final CampaignHeaderService headers = mock(CampaignHeaderService.class);
    private final CampaignRepository campaigns = mock(CampaignRepository.class);
    private final CampaignRecipientRepository recipients = mock(CampaignRecipientRepository.class);
    private final ExcelImportService service = new ExcelImportService(mock(CompanyAccess.class), reader, headers, campaigns, recipients,
            mock(SmsMessageService.class), mock(MultichannelGatewayService.class));

    @Test
    void neverReplacesRecipientsOfSentCampaigns() {
        var campaign = Campaign.builder().id(1L).status(CampaignStatus.COMPLETED).build();
        when(campaigns.findById(1L)).thenReturn(Optional.of(campaign));
        assertThrows(IllegalArgumentException.class, () -> service.importExcel(1L, file()));
        assertThrows(IllegalArgumentException.class, () -> service.importRowsIntoCampaign(campaign, List.of(), "TELEPHONE"));
        verifyNoInteractions(reader, recipients, headers);
    }

    @Test
    void usesSavedSheetAndHeaderLineAndRejectsAnEmptyRecipientFile() {
        var campaign = Campaign.builder().id(1L).company(Company.builder().id(1L).build())
                .importConfiguration(Map.of("sheetIndex", 1, "headerRow", 3)).build();
        when(campaigns.findById(1L)).thenReturn(Optional.of(campaign));
        var file = file();
        when(reader.read(file, 1, 3)).thenReturn(new ExcelTableReader.Table(List.of("TELEPHONE"), Map.of(), List.of(), List.of("Notes", "Clients")));
        assertThrows(IllegalArgumentException.class, () -> service.importExcel(1L, file));
        verify(reader).read(file, 1, 3);
        verifyNoInteractions(recipients);
    }

    private MockMultipartFile file() { return new MockMultipartFile("file", "contacts.xlsx", "", new byte[] { 1 }); }
}
