package andromeda.sei.ms.feature.organization.application.service;

import andromeda.sei.ms.feature.organization.adapter.in.web.dto.CreateOrganizationRequest;
import andromeda.sei.ms.feature.organization.adapter.in.web.dto.CreateOrganizationResponse;
import andromeda.sei.ms.feature.organization.application.port.out.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.role.domain.Permission;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.user.application.port.out.UserRepository;
import andromeda.sei.ms.feature.user.domain.User;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrganizationServiceTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    CreateOrganizationService service;

    @Test
    void shouldCreateOrganizationSuccessfully() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "Acme Corp", "Admin User", "admin@acme.com", "password123");

        Organization savedOrg = new Organization(orgId, "Acme Corp", "acme-corp", Instant.now());
        Role adminRole = new Role(roleId, null, "ADMIN", true, null, List.of());
        User savedUser = new User(userId, orgId, "admin@acme.com", "hashed", "Admin User", roleId, null, true, Instant.now());

        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(savedOrg);
        when(roleRepository.findDefaultByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(roleRepository.findPermissionCodesByRoleId(roleId)).thenReturn(List.of("USER_MANAGE"));
        when(jwtService.generateToken(userId, orgId, List.of("USER_MANAGE"))).thenReturn("jwt-token");

        CreateOrganizationResponse response = service.execute(request);

        assertThat(response.organizationId()).isEqualTo(orgId);
        assertThat(response.slug()).isEqualTo("acme-corp");
        assertThat(response.accessToken()).isEqualTo("jwt-token");
    }

    @Test
    void shouldThrowWhenSlugAlreadyExists() {
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "Acme Corp", "Admin", "admin@acme.com", "password");

        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(true);

        assertThatThrownBy(() -> service.execute(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }
}
