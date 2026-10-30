package com.calipso.auth;

import com.calipso.companyuser.CompanyUser;
import com.calipso.companyuser.CompanyUserRepository;
import com.calipso.companyuser.CompanyUserRole;
import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PlatformAdminBootstrap implements ApplicationRunner {
    private final AccessSettings settings;
    private final CompanyUserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var bootstrap = settings.getBootstrap();
        if (bootstrap == null || bootstrap.getUsername() == null || bootstrap.getUsername().isBlank()) {
            return;
        }
        var existing = users.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(
                bootstrap.getUsername().trim(), bootstrap.getUsername().trim());
        CompanyUser admin;
        if (existing.isPresent()) {
            admin = existing.get();
            if (admin.getRole() != CompanyUserRole.ADMIN || admin.getEmail() == null
                    || !admin.getEmail().equalsIgnoreCase(bootstrap.getEmail())) {
                throw new IllegalStateException(
                        "Le compte déjà présent ne correspond pas au rôle ADMIN et à l’adresse e-mail configurés pour le bootstrap."
                );
            }
        } else {
            if (bootstrap.getPassword() == null || bootstrap.getPassword().length() < 12
                    || bootstrap.getEmail() == null || bootstrap.getEmail().isBlank()) {
                throw new IllegalStateException(
                        "La première création exige CALIPSO_SECURITY_BOOTSTRAP_PASSWORD (12 caractères minimum) "
                                + "et CALIPSO_SECURITY_BOOTSTRAP_EMAIL."
                );
            }
            Company company = companies.save(Company.builder()
                    .name(bootstrap.getCompanyName())
                    .contactName(bootstrap.getFullName())
                    .email(bootstrap.getEmail())
                    .smsBalance(0)
                    .active(true)
                    .build());
            admin = users.save(CompanyUser.builder()
                    .company(company)
                    .fullName(bootstrap.getFullName())
                    .username(bootstrap.getUsername().trim())
                    .email(bootstrap.getEmail().trim())
                    .passwordHash(passwordEncoder.encode(bootstrap.getPassword()))
                    .role(CompanyUserRole.ADMIN)
                    .active(true)
                    .build());
        }
        settings.getPlatformAdminUserIds().add(admin.getId());
        bootstrap.setPassword(null);
    }
}
