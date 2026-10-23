package com.calipso.auth;

import com.calipso.campaign.CampaignChannel;
import com.calipso.companyuser.CompanyUser;
import com.calipso.companyuser.CompanyUserRepository;
import com.calipso.companyuser.CompanyUserRole;
import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import com.calipso.sms.MultichannelGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int TOKEN_BYTES = 48;
    private static final int TOKEN_TTL_HOURS = 24;
    private static final int RESET_CODE_TTL_MINUTES = 15;

    private final CompanyUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MultichannelGatewayService gatewayService;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginResponse login(LoginRequest request) {
        String id = request.username().trim();
        CompanyUser user = userRepository.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(id, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides"));

        if (!Boolean.TRUE.equals(user.getActive())
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }

        return issueToken(user);
    }

    public LoginResponse setupAdmin(SetupAdminRequest request) {
        return createWorkspaceAccount(request, !userRepository.existsByPasswordHashIsNotNull());
    }

    public LoginResponse register(SetupAdminRequest request) {
        return createWorkspaceAccount(request, !userRepository.existsByPasswordHashIsNotNull());
    }

    public ForgotPasswordResponse requestPasswordReset(ForgotPasswordRequest request) {
        String id = request.identifier().trim();
        CompanyUser user = userRepository.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(id, id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Aucun compte trouvé pour cet identifiant ou adresse email."
                ));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce compte utilisateur est actuellement suspendu.");
        }

        String otpCode = String.format("%06d", 100000 + secureRandom.nextInt(900000));
        user.setResetCode(otpCode);
        user.setResetCodeExpiresAt(LocalDateTime.now().plusMinutes(RESET_CODE_TTL_MINUTES));
        userRepository.save(user);

        CampaignChannel chosenChannel = request.channel() == null ? CampaignChannel.EMAIL : request.channel();
        String destination = resolveUserDestination(user, chosenChannel);

        String message = "Bonjour " + user.getFullName() + ",\n\n"
                + "Votre code de sécurité pour réinitialiser votre mot de passe Calipso Studio est : " + otpCode + "\n"
                + "Ce code est valable pendant " + RESET_CODE_TTL_MINUTES + " minutes.";

        try {
            gatewayService.dispatch(
                    chosenChannel,
                    message,
                    "Code de réinitialisation de votre mot de passe Calipso : " + otpCode,
                    user.getCompany().getSenderPhone(),
                    destination,
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            log.warn("[Auth Reset] Envoi externe ignoré, code OTP généré: {}", e.getMessage());
        }

        log.info("[Auth Reset] Code OTP de récupération pour '{}' ({}) = {}", user.getUsername(), chosenChannel, otpCode);

        return new ForgotPasswordResponse(
                "Un code de sécurité à 6 chiffres a été envoyé via " + chosenChannel.name() + ".",
                maskDestination(destination),
                chosenChannel,
                otpCode
        );
    }

    public LoginResponse confirmPasswordReset(ResetPasswordRequest request) {
        String id = request.identifier().trim();
        CompanyUser user = userRepository.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(id, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));

        if (user.getResetCode() == null || user.getResetCodeExpiresAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune demande de réinitialisation active pour ce compte.");
        }

        if (user.getResetCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce code de sécurité a expiré. Veuillez en demander un nouveau.");
        }

        if (!user.getResetCode().equals(request.code().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code de sécurité incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setResetCode(null);
        user.setResetCodeExpiresAt(null);
        userRepository.save(user);

        return issueToken(user);
    }

    public void changePassword(CompanyUser user, ChangePasswordRequest request) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée");
        }
        if (user.getPasswordHash() != null && !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Votre mot de passe actuel est incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private String resolveUserDestination(CompanyUser user, CampaignChannel channel) {
        if (channel == CampaignChannel.EMAIL) {
            return user.getEmail() != null && !user.getEmail().isBlank()
                    ? user.getEmail()
                    : (user.getCompany().getEmail() != null ? user.getCompany().getEmail() : "utilisateur@calipso.ci");
        }
        return user.getPhone() != null && !user.getPhone().isBlank()
                ? user.getPhone()
                : (user.getCompany().getPhone() != null ? user.getCompany().getPhone() : "+2250700000000");
    }

    private String maskDestination(String dest) {
        if (dest == null || dest.length() < 4) {
            return "***";
        }
        if (dest.contains("@")) {
            int at = dest.indexOf('@');
            String prefix = dest.substring(0, Math.min(2, at));
            return prefix + "***" + dest.substring(at);
        }
        return dest.substring(0, Math.min(6, dest.length() - 2)) + "•••" + dest.substring(dest.length() - 2);
    }

    private LoginResponse createWorkspaceAccount(SetupAdminRequest request, boolean isFirstPlatformUser) {
        if (userRepository.existsByUsernameIgnoreCase(request.username().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce nom d'utilisateur est déjà utilisé");
        }

        Company company = companyRepository.save(Company.builder()
                .name(request.companyName().trim())
                .contactName(request.fullName().trim())
                .email(request.email().trim())
                .smsBalance(50)
                .active(true)
                .build());

        CompanyUser user = userRepository.save(CompanyUser.builder()
                .company(company)
                .fullName(request.fullName().trim())
                .username(request.username().trim())
                .email(request.email().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(CompanyUserRole.ADMIN)
                .active(true)
                .build());

        return issueToken(user);
    }

    public CompanyUser authenticateToken(String token) {
        return tokenRepository.findByToken(token)
                .filter(AuthToken::isActive)
                .map(AuthToken::getUser)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .orElse(null);
    }

    public void logout(String token) {
        tokenRepository.findByToken(token).ifPresent(authToken -> {
            authToken.setRevokedAt(LocalDateTime.now());
            tokenRepository.save(authToken);
        });
    }

    private LoginResponse issueToken(CompanyUser user) {
        AuthToken authToken = AuthToken.builder()
                .token(generateToken())
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(TOKEN_TTL_HOURS))
                .build();

        AuthToken saved = tokenRepository.save(authToken);
        return new LoginResponse(saved.getToken(), saved.getExpiresAt(), AuthUserResponse.from(user));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
