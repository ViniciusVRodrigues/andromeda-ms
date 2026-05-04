package andromeda.sei.ms.feature.organization.application.service;

import andromeda.sei.ms.feature.organization.adapter.in.web.dto.CreateOrganizationRequest;
import andromeda.sei.ms.feature.organization.adapter.in.web.dto.CreateOrganizationResponse;
import andromeda.sei.ms.feature.organization.application.port.in.CreateOrganizationUseCase;
import andromeda.sei.ms.feature.organization.application.port.out.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.role.application.port.out.RoleRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.user.application.port.out.UserRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.shared.exception.BusinessException;
import andromeda.sei.ms.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateOrganizationService implements CreateOrganizationUseCase {

    private static final String ADMIN_ROLE_NAME = "ADMIN";

    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public CreateOrganizationResponse execute(CreateOrganizationRequest request) {
        Organization org = Organization.create(request.organizationName());

        if (organizationRepository.existsBySlug(org.slug())) {
            throw new BusinessException("Organization with slug '" + org.slug() + "' already exists");
        }

        Organization savedOrg = organizationRepository.save(org);

        Role adminRole = roleRepository.findDefaultByName(ADMIN_ROLE_NAME)
                .orElseThrow(() -> new BusinessException("Default ADMIN role not found"));

        String passwordHash = passwordEncoder.encode(request.adminPassword());
        User adminUser = User.create(savedOrg.id(), request.adminEmail(), passwordHash, request.adminName(), adminRole.id());
        User savedUser = userRepository.save(adminUser);

        List<String> permissions = roleRepository.findPermissionCodesByRoleId(adminRole.id());
        String token = jwtService.generateToken(savedUser.id(), savedOrg.id(), permissions);

        return new CreateOrganizationResponse(savedOrg.id(), savedOrg.slug(), savedUser.id(), token);
    }
}
