package andromeda.sei.ms.feature.auth.application.service;

import andromeda.sei.ms.feature.auth.adapter.in.web.dto.LoginRequest;
import andromeda.sei.ms.feature.auth.adapter.in.web.dto.LoginResponse;
import andromeda.sei.ms.feature.auth.application.port.out.UserAuthRepository;
import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.shared.config.JwtProperties;
import andromeda.sei.ms.shared.exception.BusinessException;
import andromeda.sei.ms.shared.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock UserAuthRepository userAuthRepository;
    @Mock RoleRepository roleRepository;
    @Mock JwtService jwtService;
    @Mock JwtProperties jwtProperties;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks LoginService service;

    @Test
    void shouldLoginSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        User user = new User(userId, orgId, "user@test.com", "hashed", "Test User", roleId, null, true, Instant.now());
        LoginRequest request = new LoginRequest("user@test.com", "password");

        when(userAuthRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(roleRepository.findPermissionCodesByRoleId(roleId)).thenReturn(List.of("AI_READ_OWN"));
        when(jwtService.generateToken(userId, orgId, List.of("AI_READ_OWN"))).thenReturn("access-token");
        when(jwtProperties.expirationMs()).thenReturn(86400000L);

        LoginResponse response = service.execute(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresIn()).isEqualTo(86400L);
    }

    @Test
    void shouldThrowOnInvalidCredentials() {
        User user = new User(UUID.randomUUID(), UUID.randomUUID(), "user@test.com", "hashed", "Test", UUID.randomUUID(), null, true, Instant.now());
        LoginRequest request = new LoginRequest("user@test.com", "wrong");

        when(userAuthRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userAuthRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new LoginRequest("x@y.com", "pass")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowWhenUserDeactivated() {
        User user = new User(UUID.randomUUID(), UUID.randomUUID(), "user@test.com", "hashed", "Test", UUID.randomUUID(), null, false, Instant.now());
        when(userAuthRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.execute(new LoginRequest("user@test.com", "pass")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deactivated");
    }
}
