package com.calipso.companyuser;

import com.calipso.auth.AuthTokenRepository;
import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/company-users")
@RequiredArgsConstructor
public class CompanyUserController {

    private final CompanyUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping
    public CompanyUser create(@RequestBody @Valid CreateCompanyUserRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));

        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new RuntimeException("Ce nom d'utilisateur existe déjà");
        }

        CompanyUser user = CompanyUser.builder()
                .company(company)
                .fullName(request.fullName())
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .role(request.role() == null ? CompanyUserRole.OPERATOR : request.role())
                .active(true)
                .build();

        return userRepository.save(user);
    }

    @GetMapping("/company/{companyId}")
    public List<CompanyUser> findByCompany(@PathVariable Long companyId) {
        return userRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @PatchMapping("/{userId}/active")
    @Transactional
    public CompanyUser updateStatus(@PathVariable Long userId, @RequestParam boolean active) {
        CompanyUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        user.setActive(active);
        if (!active) {
            authTokenRepository.deleteByUserId(userId);
        }
        return userRepository.save(user);
    }

    @PatchMapping("/{userId}/role")
    public CompanyUser updateRole(@PathVariable Long userId, @RequestParam CompanyUserRole role) {
        CompanyUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        user.setRole(role);
        return userRepository.save(user);
    }

    @PostMapping("/{userId}/reset-password")
    public Map<String, String> adminResetPassword(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body
    ) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nouveau mot de passe est obligatoire");
        }
        CompanyUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        user.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        user.setResetCode(null);
        user.setResetCodeExpiresAt(null);
        userRepository.save(user);

        return Map.of("message", "Mot de passe réinitialisé pour " + user.getFullName());
    }

    @DeleteMapping("/{userId}")
    @Transactional
    public void deleteUser(@PathVariable Long userId) {
        authTokenRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }
}
