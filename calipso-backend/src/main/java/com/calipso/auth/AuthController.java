package com.calipso.auth;

import com.calipso.companyuser.CompanyUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CompanyAccess access;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/setup")
    public LoginResponse setup(@RequestBody @Valid SetupAdminRequest request) {
        return authService.setupAdmin(request);
    }

    @PostMapping("/register")
    public LoginResponse register(@RequestBody @Valid SetupAdminRequest request) {
        return authService.register(request);
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/reset-password")
    public LoginResponse resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return authService.confirmPasswordReset(request);
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(
            @AuthenticationPrincipal CompanyUser user,
            @RequestBody @Valid ChangePasswordRequest request
    ) {
        authService.changePassword(user, request);
        return Map.of("message", "Votre mot de passe a été modifié avec succès.");
    }

    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal CompanyUser user) {
        return AuthUserResponse.from(user, access.isPlatformAdmin(user));
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = authorization == null ? "" : authorization.replace("Bearer ", "").trim();
        authService.logout(token);
    }
}
