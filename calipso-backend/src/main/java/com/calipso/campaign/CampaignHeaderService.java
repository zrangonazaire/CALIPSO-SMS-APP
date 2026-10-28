package com.calipso.campaign;

import com.calipso.auth.CompanyAccess;

import com.calipso.excel.HeaderNames;
import com.calipso.config.DataType;
import com.calipso.excelvariable.ExcelVariable;
import com.calipso.excelvariable.ExcelVariableRepository;
import com.calipso.importprofile.HeaderModelService;
import com.calipso.importprofile.ExcelImportProfile;
import com.calipso.sms.MultichannelGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CampaignHeaderService {

    private final CompanyAccess access;
    private final HeaderModelService models;
    private final ExcelVariableRepository variables;
    private final MultichannelGatewayService gateway;

    public record Prepared(ExcelImportProfile profile, List<Map<String, Object>> rows,
                           String recipientColumn, Map<String, Object> snapshot) {}

    public Prepared prepare(WizardCampaignRequest request) {
        access.requireCompany(request.companyId());
        var rows = request.rows() == null ? List.<Map<String, Object>>of() : request.rows();
        if (rows.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Ligne de destinataire invalide.");
        var channel = request.channel() == null ? CampaignChannel.SMS : request.channel();
        Map<String, Object> fileOptions = request.sheetIndex() == null && request.headerRow() == null ? null : Map.of(
                "sheetIndex", request.sheetIndex() == null ? 0 : request.sheetIndex(), "headerRow", request.headerRow() == null ? 1 : request.headerRow());
        if (request.profileId() == null) return new Prepared(null, rows, request.recipientColumn(), fileOptions);
        var profile = models.activeProfile(request.profileId());
        if (!profile.getCompany().getId().equals(request.companyId())) throw new IllegalArgumentException("Le modèle appartient à une autre entreprise.");
        var columns = variables.findByProfileIdAndActiveTrueOrderByColumnOrderAscIdAsc(profile.getId());
        if (columns.isEmpty()) throw new IllegalArgumentException("Ce modèle ne contient aucun en-tête.");
        Set<String> headers = new HashSet<>();
        rows.forEach(r -> headers.addAll(r.keySet()));
        Map<String, String> mapping = new LinkedHashMap<>();
        for (var column : columns) {
            String source = request.columnMapping() == null ? null : request.columnMapping().get(column.getCode());
            if (source == null) source = headers.stream().filter(h -> HeaderNames.normalize(h).equals(HeaderNames.normalize(HeaderModelService.headerName(column)))
                    || HeaderNames.normalize(h).equals(column.getCode())).sorted().findFirst().orElse("");
            if (!source.isEmpty() && !headers.contains(source)) throw new IllegalArgumentException("Colonne source introuvable : " + source);
            if (source.isEmpty() && Boolean.TRUE.equals(column.getRequired())) throw new IllegalArgumentException("Colonne obligatoire absente : " + column.getCode());
            mapping.put(column.getCode(), source);
        }
        List<String> used = mapping.values().stream().filter(v -> !v.isEmpty()).toList();
        if (new HashSet<>(used).size() != used.size()) throw new IllegalArgumentException("Une colonne source ne peut être associée à plusieurs variables.");
        String target = request.recipientColumn();
        if (channel != CampaignChannel.EMAIL) {
            target = columns.stream().filter(v -> Boolean.TRUE.equals(v.getPhone())).map(ExcelVariable::getCode)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Désignez la colonne destinataire SMS dans le modèle."));
        } else {
            final String requestedTarget = target;
            target = mapping.entrySet().stream().filter(e -> e.getValue().equals(requestedTarget)).map(Map.Entry::getKey).findFirst().orElse(target);
        }
        final String recipient = target;
        if (recipient == null || !columns.stream().anyMatch(v -> v.getCode().equals(recipient)) || mapping.getOrDefault(recipient, "").isEmpty()) {
            throw new IllegalArgumentException("Associez la colonne destinataire du canal sélectionné.");
        }
        var mapped = mapAndValidate(columns, mapping, rows, recipient, channel, request.messageTemplate(), request.emailSubject());
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (var column : columns) {
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("code", column.getCode()); definition.put("headerName", HeaderModelService.headerName(column));
            definition.put("label", column.getLabel()); definition.put("dataType", column.getDataType().name());
            definition.put("required", column.getRequired()); definition.put("phone", column.getPhone());
            definition.put("sourceColumn", mapping.get(column.getCode())); definitions.add(definition);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(fileOptions == null ? Map.of() : fileOptions);
        snapshot.put("profileId", profile.getId()); snapshot.put("profileName", profile.getName()); snapshot.put("columns", definitions);
        return new Prepared(profile, mapped, recipient, snapshot);
    }

    public Prepared prepareReimport(Campaign campaign, FileAnalysisResponse analysis) {
        access.requireCampaign(campaign);
        Map<String, Object> snapshot = campaign.getImportConfiguration();
        if (snapshot == null) {
            return prepare(new WizardCampaignRequest(campaign.getCompany().getId(), campaign.getName(), campaign.getDescription(),
                    campaign.resolvedChannel(), campaign.resolvedTemplateContent(), campaign.getEmailSubject(), campaign.getRecipientColumn(),
                    campaign.getMediaUrl(), campaign.getCtaLabel(), campaign.getCtaUrl(), analysis.allRows(),
                    campaign.getProfile() == null ? null : campaign.getProfile().getId(), null, null, null, false));
        }
        if (!snapshot.containsKey("columns")) return new Prepared(null, analysis.allRows(), campaign.getRecipientColumn(), snapshot);
        if (!(snapshot.get("columns") instanceof List<?> definitions)) throw new IllegalArgumentException("Configuration de campagne invalide.");
        List<ExcelVariable> columns = new ArrayList<>();
        Map<String, String> mapping = new LinkedHashMap<>();
        List<Map<String, Object>> importedDefinitions = new ArrayList<>();
        for (Object entry : definitions) {
            if (!(entry instanceof Map<?, ?> definition)) throw new IllegalArgumentException("Configuration de colonne invalide.");
            String code = Objects.toString(definition.get("code"), "");
            String headerName = Objects.toString(definition.get("headerName"), code);
            String storedSource = Objects.toString(definition.get("sourceColumn"), "");
            var column = ExcelVariable.builder().code(code).headerName(headerName)
                    .label(Objects.toString(definition.get("label"), code))
                    .dataType(DataType.valueOf(Objects.toString(definition.get("dataType"), "TEXT")))
                    .required(Boolean.TRUE.equals(definition.get("required"))).phone(Boolean.TRUE.equals(definition.get("phone"))).build();
            columns.add(column);
            String source = analysis.headers().contains(storedSource) ? storedSource
                    : analysis.headers().stream().filter(h -> HeaderNames.normalize(h).equals(HeaderNames.normalize(headerName))
                            || HeaderNames.normalize(h).equals(code)).findFirst().orElse("");
            if (Boolean.TRUE.equals(column.getRequired()) && source.isEmpty()) throw new IllegalArgumentException("Colonne obligatoire absente : " + code);
            mapping.put(code, source);
            Map<String, Object> importedDefinition = new LinkedHashMap<>();
            definition.forEach((key, value) -> importedDefinition.put(key.toString(), value));
            importedDefinition.put("sourceColumn", source);
            importedDefinitions.add(importedDefinition);
        }
        var rows = mapAndValidate(columns, mapping, analysis.allRows(), campaign.getRecipientColumn(), campaign.resolvedChannel(),
                campaign.resolvedTemplateContent(), campaign.getEmailSubject());
        Map<String, Object> importedSnapshot = new LinkedHashMap<>(snapshot);
        importedSnapshot.put("columns", importedDefinitions);
        return new Prepared(campaign.getProfile(), rows, campaign.getRecipientColumn(), importedSnapshot);
    }

    private List<Map<String, Object>> mapAndValidate(List<ExcelVariable> columns, Map<String, String> mapping,
            List<Map<String, Object>> rows, String recipient, CampaignChannel channel, String message, String subject) {
        var sources = mapping.values().stream().filter(v -> !v.isEmpty()).toList();
        if (new HashSet<>(sources).size() != sources.size()) throw new IllegalArgumentException("Une colonne source ne peut être associée à plusieurs variables.");
        if (recipient == null || mapping.getOrDefault(recipient, "").isEmpty()) throw new IllegalArgumentException("Colonne destinataire absente du fichier.");
        var tokens = Pattern.compile("\\{\\{\\s*([\\w-]+)\\s*}}").matcher(message + " "
                + (channel == CampaignChannel.EMAIL ? Objects.toString(subject, "") : ""));
        while (tokens.find()) {
            String code = tokens.group(1).toUpperCase(Locale.ROOT);
            if (!mapping.containsKey(code) || mapping.get(code).isEmpty()) throw new IllegalArgumentException("Variable du message sans correspondance : " + code);
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (var column : columns) {
                Object value = rows.get(i).get(mapping.get(column.getCode()));
                String text = Objects.toString(value, "").trim();
                if ((Boolean.TRUE.equals(column.getRequired()) && text.isEmpty()) || (!text.isEmpty() && !validValue(column, text))) {
                    throw new IllegalArgumentException("Ligne de données " + (i + 1) + " : valeur manquante ou invalide pour " + column.getCode());
                }
                row.put(column.getCode(), text);
            }
            String target = Objects.toString(row.get(recipient), "");
            if (!gateway.isValidTarget(channel, target) || (channel == CampaignChannel.EMAIL && !validEmail(target))) {
                throw new IllegalArgumentException("Ligne de données " + (i + 1) + " : destinataire invalide pour " + channel);
            }
            mapped.add(row);
        }
        return mapped;
    }

    private boolean validValue(ExcelVariable column, String text) {
        return switch (column.getDataType()) {
            case TEXT -> true;
            case NUMBER -> text.replaceAll("[\\s\\u00a0\\u202f]", "").matches("[+-]?(?:\\d+(?:[.,]\\d+)?|[.,]\\d+)");
            case PHONE -> gateway.isValidTarget(CampaignChannel.SMS, text);
            case EMAIL -> validEmail(text);
            case BOOLEAN -> List.of("true", "false", "oui", "non", "1", "0").contains(text.toLowerCase(Locale.ROOT));
            case DATE -> validDate(text);
        };
    }

    private boolean validDate(String text) {
        for (String format : List.of("uuuu-MM-dd", "dd/MM/uuuu")) {
            try { LocalDate.parse(text, DateTimeFormatter.ofPattern(format).withResolverStyle(ResolverStyle.STRICT)); return true; }
            catch (java.time.format.DateTimeParseException ignored) { }
        }
        return false;
    }

    private boolean validEmail(String text) {
        return text.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}
