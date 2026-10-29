package com.calipso.importprofile;

import com.calipso.auth.CompanyAccess;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.config.DataType;
import com.calipso.excel.ExcelTableReader;
import com.calipso.excelvariable.ExcelVariable;
import com.calipso.excelvariable.ExcelVariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeaderModelServiceTest {
    private final ExcelImportProfileRepository profiles = mock(ExcelImportProfileRepository.class);
    private final ExcelVariableRepository variables = mock(ExcelVariableRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final HeaderModelService service = new HeaderModelService(mock(CompanyAccess.class), profiles, variables, companies);
    private ExcelImportProfile profile;
    private ExcelVariable existing;

    @BeforeEach
    void setup() {
        var company = Company.builder().id(1L).name("École").build();
        profile = ExcelImportProfile.builder().id(7L).company(company).name("Clients").active(true).build();
        existing = ExcelVariable.builder().id(8L).profile(profile).company(company).code("TELEPHONE")
                .label("Téléphone").headerName("Téléphone client").dataType(DataType.PHONE).phone(true).required(true).active(true).build();
        when(companies.findById(1L)).thenReturn(Optional.of(company));
        when(profiles.findById(7L)).thenReturn(Optional.of(profile));
        when(profiles.save(any())).thenAnswer(invocation -> { var p = invocation.<ExcelImportProfile>getArgument(0); if (p.getId() == null) p.setId(9L); return p; });
        when(variables.findById(8L)).thenReturn(Optional.of(existing));
        when(variables.findByProfileIdAndCode(7L, "TELEPHONE")).thenReturn(Optional.of(existing));
        when(variables.findByProfileIdAndActiveTrue(7L)).thenReturn(List.of(existing));
        when(variables.findByProfileIdAndActiveTrueOrderByColumnOrderAscIdAsc(7L)).thenReturn(List.of(existing));
    }

    @Test
    void updatesExistingColumnAndEnforcesPhoneTypeAndRequired() {
        service.save(7L, new SaveHeaderModelRequest(1L, "Nouveau nom", "Description", List.of(
                new SaveHeaderModelRequest.Header(8L, "telephone", "Mobile", "Numéro", DataType.TEXT, false, true))));
        assertEquals("Nouveau nom", profile.getName()); assertEquals("Mobile", existing.getHeaderName());
        assertEquals(DataType.PHONE, existing.getDataType()); assertTrue(existing.getRequired()); assertTrue(existing.getActive());
        assertEquals(0, existing.getColumnOrder());
    }

    @Test
    void rejectsDuplicateCodesAndMultipleRecipientColumnsBeforeWriting() {
        var a = new SaveHeaderModelRequest.Header(null, "PRENOM", "Prénom", "Prénom", DataType.TEXT, false, false);
        var b = new SaveHeaderModelRequest.Header(null, "prenom", "Autre", "Autre", DataType.TEXT, false, false);
        assertThrows(IllegalArgumentException.class, () -> service.save(null, new SaveHeaderModelRequest(1L, "Clients", null, List.of(a, b))));
        var phoneA = new SaveHeaderModelRequest.Header(null, "TEL_1", "Téléphone 1", "Téléphone 1", DataType.PHONE, true, true);
        var phoneB = new SaveHeaderModelRequest.Header(null, "TEL_2", "Téléphone 2", "Téléphone 2", DataType.PHONE, true, true);
        assertThrows(IllegalArgumentException.class, () -> service.save(null, new SaveHeaderModelRequest(1L, "Clients", null, List.of(phoneA, phoneB))));
        verify(profiles, never()).save(any());
    }

    @Test
    void duplicatesStructureWithNewColumnIds() {
        var copy = service.duplicate(7L);
        assertEquals("Clients (copie)", copy.getName()); assertEquals(9L, copy.getId());
        var captor = org.mockito.ArgumentCaptor.forClass(ExcelVariable.class);
        verify(variables).save(captor.capture());
        assertNull(captor.getValue().getId()); assertEquals("Téléphone client", captor.getValue().getHeaderName());
        assertEquals(copy, captor.getValue().getProfile());
    }

    @Test
    void downloadsAHeaderOnlyWorkbookAndArchivesWithoutDeletingColumns() {
        var bytes = service.download(7L);
        var table = new ExcelTableReader().read(new MockMultipartFile("file", "modele.xlsx", "", bytes), 0, 1);
        assertEquals("Téléphone client", table.headerLabels().get("TELEPHONE_CLIENT")); assertTrue(table.rows().isEmpty());
        service.archive(7L); assertFalse(profile.getActive());
        verify(variables, never()).delete(any());
        assertThrows(IllegalArgumentException.class, () -> service.activeProfile(7L));
    }
}
