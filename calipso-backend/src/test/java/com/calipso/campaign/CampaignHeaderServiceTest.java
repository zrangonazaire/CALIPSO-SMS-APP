package com.calipso.campaign;

import com.calipso.auth.CompanyAccess;

import com.calipso.compagny.Company;
import com.calipso.config.DataType;
import com.calipso.excelvariable.ExcelVariable;
import com.calipso.excelvariable.ExcelVariableRepository;
import com.calipso.importprofile.ExcelImportProfile;
import com.calipso.importprofile.HeaderModelService;
import com.calipso.sms.MultichannelGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampaignHeaderServiceTest {
    private final HeaderModelService models = mock(HeaderModelService.class);
    private final ExcelVariableRepository repository = mock(ExcelVariableRepository.class);
    private final MultichannelGatewayService gateway = mock(MultichannelGatewayService.class);
    private final CampaignHeaderService service = new CampaignHeaderService(mock(CompanyAccess.class), models, repository, gateway);
    private ExcelVariable phone;
    private ExcelVariable amount;

    @BeforeEach
    void setup() {
        var company = Company.builder().id(1L).name("École").build();
        var profile = ExcelImportProfile.builder().id(7L).name("Scolarité").company(company).active(true).build();
        when(models.activeProfile(7L)).thenReturn(profile);
        phone = ExcelVariable.builder().code("TELEPHONE").headerName("Téléphone client").label("Téléphone")
                .dataType(DataType.PHONE).required(true).phone(true).build();
        amount = ExcelVariable.builder().code("MONTANT").headerName("Montant dû").label("Montant")
                .dataType(DataType.NUMBER).required(true).phone(false).build();
        when(repository.findByProfileIdAndActiveTrueOrderByColumnOrderAscIdAsc(7L)).thenReturn(List.of(phone, amount));
        when(gateway.isValidTarget(eq(CampaignChannel.SMS), anyString())).thenAnswer(invocation -> invocation.<String>getArgument(1).replaceAll("\\D", "").length() >= 8);
    }

    private WizardCampaignRequest request(Map<String, String> mapping, Map<String, Object> row, String message) {
        return new WizardCampaignRequest(1L, "Relance", null, CampaignChannel.SMS, message, null,
                "TELEPHONE", null, null, null, List.of(row), 7L, mapping, 0, 1, false);
    }

    @Test
    void mapsRenamedColumnsAndFreezesConfigurationWithoutMutatingSourceRows() {
        Map<String, Object> row = new LinkedHashMap<>(Map.of("PORTABLE", "0701234567", "SOLDE", "25 000,50"));
        var result = service.prepare(request(Map.of("TELEPHONE", "PORTABLE", "MONTANT", "SOLDE"), row, "Solde {{MONTANT}}"));
        assertEquals("0701234567", result.rows().getFirst().get("TELEPHONE"));
        assertEquals("25 000,50", result.rows().getFirst().get("MONTANT"));
        assertFalse(row.containsKey("TELEPHONE"));
        assertEquals("Scolarité", result.snapshot().get("profileName"));
        amount.setCode("NOUVEAU");
        var definition = (Map<?, ?>) ((List<?>) result.snapshot().get("columns")).get(1);
        assertEquals("MONTANT", definition.get("code"));
        assertEquals("SOLDE", definition.get("sourceColumn"));
    }

    @Test
    void automaticallyMatchesHeaderNamesIndependentOfColumnOrder() {
        var result = service.prepare(request(null, Map.of("MONTANT_DU", "0", "TELEPHONE_CLIENT", "0701234567"), "Solde {{MONTANT}}"));
        assertEquals("0", result.rows().getFirst().get("MONTANT"));
        assertEquals("TELEPHONE", result.recipientColumn());
    }

    @Test
    void rejectsMissingRequiredColumnsAndDuplicateSources() {
        var row = Map.<String, Object>of("PORTABLE", "0701234567");
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request(Map.of("TELEPHONE", "PORTABLE", "MONTANT", ""), row, "Bonjour")));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request(Map.of("TELEPHONE", "PORTABLE", "MONTANT", "PORTABLE"), row, "Bonjour")));
    }

    @Test
    void rejectsInvalidValuesAndUnknownMessageVariables() {
        var mapping = Map.of("TELEPHONE", "PORTABLE", "MONTANT", "SOLDE");
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request(mapping, Map.of("PORTABLE", "0701234567", "SOLDE", "abc"), "Bonjour")));
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request(mapping, Map.of("PORTABLE", "0701234567", "SOLDE", "10"), "Bonjour {{PRENOM}}")));
    }

    @Test
    void rejectsModelsFromAnotherCompany() {
        var other = ExcelImportProfile.builder().company(Company.builder().id(2L).build()).build();
        when(models.activeProfile(7L)).thenReturn(other);
        assertThrows(IllegalArgumentException.class, () -> service.prepare(request(Map.of(), Map.of(), "Bonjour")));
    }

    @Test
    void leavesFreeImportsCompatible() {
        var request = new WizardCampaignRequest(1L, "Libre", null, CampaignChannel.SMS, "Bonjour", null,
                "PHONE", null, null, null, List.of(Map.of("PHONE", "0701234567")), null, null, null, null, false);
        var result = service.prepare(request);
        assertNull(result.profile()); assertNull(result.snapshot()); assertEquals(request.rows(), result.rows());
    }

    @Test
    void reimportsUsingFrozenConfigurationAfterModelEditsOrArchiving() {
        var original = service.prepare(request(Map.of("TELEPHONE", "PORTABLE", "MONTANT", "SOLDE"),
                Map.of("PORTABLE", "0701234567", "SOLDE", "10"), "Solde {{MONTANT}}"));
        var campaign = Campaign.builder().profile(original.profile()).importConfiguration(original.snapshot())
                .recipientColumn("TELEPHONE").messageTemplate("Solde {{MONTANT}}").channel(CampaignChannel.SMS).build();
        amount.setCode("NOUVEAU"); original.profile().setActive(false);
        clearInvocations(models, repository);
        var rows = List.<Map<String, Object>>of(Map.of("TELEPHONE_CLIENT", "0701234567", "MONTANT_DU", "20"));
        var analysis = new FileAnalysisResponse("nouveau.csv", List.of("MONTANT_DU", "TELEPHONE_CLIENT"), null, null, null,
                1, rows, rows, Map.of(), List.of("CSV"), 0, 1);
        var result = service.prepareReimport(campaign, analysis);
        assertEquals("20", result.rows().getFirst().get("MONTANT"));
        assertFalse(result.rows().getFirst().containsKey("NOUVEAU"));
        verifyNoInteractions(models, repository);
        var definition = (Map<?, ?>) ((List<?>) result.snapshot().get("columns")).get(1);
        assertEquals("MONTANT_DU", definition.get("sourceColumn"));
        var originalDefinition = (Map<?, ?>) ((List<?>) original.snapshot().get("columns")).get(1);
        assertEquals("SOLDE", originalDefinition.get("sourceColumn"));
    }
}
