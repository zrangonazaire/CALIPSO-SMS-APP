package com.calipso.auth;

import com.calipso.companyuser.CompanyUser;
import com.calipso.companyuser.CompanyUserRepository;
import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformAdminBootstrapTest {
    @Test
    void createsAndAuthorizesConfiguredAdminWithoutPersistingPlaintextPassword() throws Exception {
        var settings = new AccessSettings();
        settings.getBootstrap().setUsername("admin");
        settings.getBootstrap().setPassword("long-injected-secret");
        settings.getBootstrap().setEmail("admin@example.ci");
        var users = mock(CompanyUserRepository.class);
        var companies = mock(CompanyRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var storedUser = new AtomicReference<CompanyUser>();

        when(users.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase("admin", "admin"))
                .thenAnswer(invocation -> Optional.ofNullable(storedUser.get()));
        when(companies.save(any())).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.setId(9L);
            return company;
        });
        when(encoder.encode("long-injected-secret")).thenReturn("bcrypt-hash");
        when(users.save(any())).thenAnswer(invocation -> {
            CompanyUser user = invocation.getArgument(0);
            user.setId(11L);
            storedUser.set(user);
            return user;
        });

        new PlatformAdminBootstrap(settings, users, companies, encoder).run(new DefaultApplicationArguments());

        assertEquals("bcrypt-hash", storedUser.get().getPasswordHash());
        assertTrue(settings.getPlatformAdminUserIds().contains(11L));
        assertNull(settings.getBootstrap().getPassword());
    }

    @Test
    void reauthorizesExistingAdminWithoutRequiringBootstrapPassword() throws Exception {
        var settings = new AccessSettings();
        settings.getBootstrap().setUsername("admin");
        settings.getBootstrap().setPassword(null);
        settings.getBootstrap().setEmail("admin@example.ci");
        var existing = CompanyUser.builder().id(5L)
                .email("admin@example.ci")
                .role(com.calipso.companyuser.CompanyUserRole.ADMIN)
                .build();
        var users = mock(CompanyUserRepository.class);
        when(users.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase("admin", "admin")).thenReturn(Optional.of(existing));

        new PlatformAdminBootstrap(settings, users, mock(CompanyRepository.class), mock(PasswordEncoder.class))
                .run(new DefaultApplicationArguments());

        assertTrue(settings.getPlatformAdminUserIds().contains(5L));
        verify(users, never()).save(any());
    }
}
