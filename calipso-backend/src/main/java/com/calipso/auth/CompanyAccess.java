package com.calipso.auth;

import com.calipso.campaign.Campaign;
import com.calipso.campaign.CampaignRepository;
import com.calipso.companyuser.CompanyUser;
import com.calipso.companyuser.CompanyUserRole;
import com.calipso.compagny.Company;
import com.calipso.importprofile.ExcelImportProfile;
import com.calipso.importprofile.ExcelImportProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CompanyAccess {
    private final AccessSettings settings;
    private final ExcelImportProfileRepository profiles;
    private final CampaignRepository campaigns;

    public CompanyUser currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CompanyUser user)
                || !Boolean.TRUE.equals(user.getActive()) || user.getId() == null
                || user.getCompany() == null || user.getCompany().getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée ou absente.");
        }
        return user;
    }

    public boolean isPlatformAdmin(CompanyUser user) {
        return user != null && user.getId() != null && Boolean.TRUE.equals(user.getActive())
                && user.getRole() == CompanyUserRole.ADMIN && settings.getPlatformAdminUserIds().contains(user.getId());
    }

    public boolean isPlatformAdmin() { return isPlatformAdmin(currentUser()); }

    public boolean isPublicRegistrationEnabled() { return settings.isPublicRegistrationEnabled(); }

    public void requirePublicRegistration() {
        if (!settings.isPublicRegistrationEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Les inscriptions publiques sont fermées. Contactez votre administrateur.");
        }
    }

    public void requirePlatformAdmin() {
        if (!isPlatformAdmin()) deny();
    }

    public void requireCompany(Long companyId) {
        var user = currentUser();
        if (companyId == null || (!isPlatformAdmin(user) && !companyId.equals(user.getCompany().getId()))) deny();
    }

    public void requireCompany(Company company) { requireCompany(company == null ? null : company.getId()); }

    public void requireSameCompany(Company expected, Company linked) {
        requireCompany(expected);
        if (linked == null || !Objects.equals(expected.getId(), linked.getId())) deny();
    }

    public void requireTeamManager(Long companyId) {
        requireCompany(companyId);
        var user = currentUser();
        if (!isPlatformAdmin(user) && user.getRole() != CompanyUserRole.ADMIN && user.getRole() != CompanyUserRole.MANAGER) deny();
    }

    public void requireUserAdministration(CompanyUser target) {
        requireTeamManager(target.getCompany().getId());
        // A tenant administrator must not take over a platform account via password reset or role changes.
        if (settings.getPlatformAdminUserIds().contains(target.getId()) && !isPlatformAdmin()) deny();
    }

    public ExcelImportProfile requireProfile(Long id) {
        var profile = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil introuvable."));
        requireCompany(profile.getCompany());
        return profile;
    }

    public Campaign requireCampaign(Long id) {
        var campaign = campaigns.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable."));
        requireCampaign(campaign);
        return campaign;
    }

    public void requireCampaign(Campaign campaign) {
        requireCompany(campaign.getCompany());
        if (campaign.getProfile() != null) requireSameCompany(campaign.getCompany(), campaign.getProfile().getCompany());
        if (campaign.getTemplate() != null) {
            requireSameCompany(campaign.getCompany(), campaign.getTemplate().getCompany());
            if (campaign.getTemplate().getProfile() != null) requireSameCompany(campaign.getCompany(), campaign.getTemplate().getProfile().getCompany());
        }
        if (campaign.getPhoneVariable() != null) {
            requireSameCompany(campaign.getCompany(), campaign.getPhoneVariable().getCompany());
            requireSameCompany(campaign.getCompany(), campaign.getPhoneVariable().getProfile().getCompany());
        }
    }

    public void requirePaymentWebhook(String providedSecret) {
        String expected = settings.getPaymentWebhookSecret();
        if (expected != null && !expected.isBlank() && providedSecret != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), providedSecret.getBytes(StandardCharsets.UTF_8))) return;
        requirePlatformAdmin();
    }

    private void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès non autorisé à cet espace ou à cette opération.");
    }
}
