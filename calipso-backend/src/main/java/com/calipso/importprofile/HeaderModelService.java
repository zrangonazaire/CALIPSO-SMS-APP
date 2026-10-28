package com.calipso.importprofile;

import com.calipso.auth.CompanyAccess;

import com.calipso.compagny.CompanyRepository;
import com.calipso.config.DataType;
import com.calipso.excel.HeaderNames;
import com.calipso.excelvariable.ExcelVariable;
import com.calipso.excelvariable.ExcelVariableRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HeaderModelService {

    private final CompanyAccess access;
    private final ExcelImportProfileRepository profiles;
    private final ExcelVariableRepository variables;
    private final CompanyRepository companies;

    @Transactional
    public ExcelImportProfile save(Long id, SaveHeaderModelRequest request) {
        access.requireCompany(request.companyId());
        var company = companies.findById(request.companyId()).orElseThrow(() -> new IllegalArgumentException("Entreprise introuvable."));
        var profile = id == null ? ExcelImportProfile.builder().company(company).active(true).build() : activeProfile(id);
        if (!profile.getCompany().getId().equals(company.getId())) throw new IllegalArgumentException("Ce modèle appartient à une autre entreprise.");
        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        int phones = 0;
        for (var column : request.columns()) {
            String code = HeaderNames.normalize(column.code());
            String name = HeaderNames.normalize(column.headerName());
            if (!code.matches("[A-Z][A-Z0-9_]*")) throw new IllegalArgumentException("Code invalide : " + column.code());
            if (name.isBlank() || !codes.add(code) || !names.add(name)) throw new IllegalArgumentException("En-tête ou code en double : " + column.headerName());
            if (column.phone() && ++phones > 1) throw new IllegalArgumentException("Désignez une seule colonne destinataire SMS.");
            if (column.id() != null && !ids.add(column.id())) throw new IllegalArgumentException("Colonne en double.");
        }
        profile.setName(request.name().trim());
        profile.setDescription(request.description());
        profiles.save(profile);
        List<ExcelVariable> previous = variables.findByProfileIdAndActiveTrue(profile.getId());
        previous.forEach(v -> v.setActive(false));
        int order = 0;
        for (var column : request.columns()) {
            String code = HeaderNames.normalize(column.code());
            ExcelVariable variable = column.id() == null
                    ? variables.findByProfileIdAndCode(profile.getId(), code).orElseGet(ExcelVariable::new)
                    : variables.findById(column.id()).orElseThrow(() -> new IllegalArgumentException("Colonne introuvable."));
            if (variable.getId() != null && !variable.getProfile().getId().equals(profile.getId())) throw new IllegalArgumentException("Colonne étrangère au modèle.");
            var duplicate = variables.findByProfileIdAndCode(profile.getId(), code);
            if (duplicate.isPresent() && !Objects.equals(duplicate.get().getId(), variable.getId())) throw new IllegalArgumentException("Ce code a déjà été utilisé : " + code);
            variable.setCompany(company);
            variable.setProfile(profile);
            variable.setCode(code);
            variable.setHeaderName(column.headerName().trim());
            variable.setLabel(column.label().trim());
            variable.setColumnOrder(order++);
            variable.setDataType(column.phone() ? DataType.PHONE : column.dataType());
            variable.setRequired(column.required() || column.phone());
            variable.setPhone(column.phone());
            variable.setActive(true);
            variables.save(variable);
        }
        return profile;
    }

    public ExcelImportProfile activeProfile(Long id) {
        var profile = profiles.findById(id).filter(p -> Boolean.TRUE.equals(p.getActive()))
                .orElseThrow(() -> new IllegalArgumentException("Modèle introuvable ou archivé."));
        access.requireCompany(profile.getCompany());
        return profile;
    }

    @Transactional
    public ExcelImportProfile duplicate(Long id) {
        var source = activeProfile(id);
        var columns = variables.findByProfileIdAndActiveTrueOrderByColumnOrderAscIdAsc(id).stream()
                .map(v -> new SaveHeaderModelRequest.Header(null, v.getCode(), headerName(v), v.getLabel(), v.getDataType(),
                        Boolean.TRUE.equals(v.getRequired()), Boolean.TRUE.equals(v.getPhone()))).toList();
        return save(null, new SaveHeaderModelRequest(source.getCompany().getId(), source.getName() + " (copie)", source.getDescription(), columns));
    }

    @Transactional
    public void archive(Long id) {
        var profile = activeProfile(id);
        profile.setActive(false);
        profiles.save(profile);
    }

    public byte[] download(Long id) {
        activeProfile(id);
        var columns = variables.findByProfileIdAndActiveTrueOrderByColumnOrderAscIdAsc(id);
        if (columns.isEmpty()) throw new IllegalArgumentException("Ajoutez au moins un en-tête au modèle.");
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Destinataires");
            var row = sheet.createRow(0);
            var style = workbook.createCellStyle();
            var font = workbook.createFont(); font.setBold(true); style.setFont(font);
            for (int i = 0; i < columns.size(); i++) {
                var cell = row.createCell(i); cell.setCellValue(headerName(columns.get(i))); cell.setCellStyle(style);
                sheet.setColumnWidth(i, Math.min(60, Math.max(18, headerName(columns.get(i)).length() + 3)) * 256);
                var textStyle = workbook.createCellStyle();
                textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
                if (columns.get(i).getDataType() == DataType.PHONE) sheet.setDefaultColumnStyle(i, textStyle);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(output); return output.toByteArray();
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Impossible de générer le modèle Excel.", e); }
    }

    public static String headerName(ExcelVariable variable) {
        return variable.getHeaderName() == null || variable.getHeaderName().isBlank() ? variable.getCode() : variable.getHeaderName();
    }
}
