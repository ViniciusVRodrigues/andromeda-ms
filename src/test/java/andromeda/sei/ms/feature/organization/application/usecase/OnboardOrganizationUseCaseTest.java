package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationInput;
import andromeda.sei.ms.feature.organization.application.dto.OnboardOrganizationOutput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationSlugConflictException;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.user.application.port.PasswordHasher;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardOrganizationUseCaseTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private andromeda.sei.ms.feature.usage.application.usecase.RecordLoginUseCase recordLoginUseCase;
    @Mock private andromeda.sei.ms.feature.usage.application.usecase.LinkFunnelUseCase linkFunnelUseCase;

    @InjectMocks
    private OnboardOrganizationUseCase useCase;

    private static final Instant NOW = Instant.now();

    private OnboardOrganizationInput buildInput(List<OnboardOrganizationInput.InviteData> invites) {
        return new OnboardOrganizationInput(
                new OnboardOrganizationInput.OrgData("Acme Corp", "Tecnologia", "Brasil"),
                new OnboardOrganizationInput.AdminData("admin@acme.com", "Maria Admin", "Secure@1"),
                new OnboardOrganizationInput.ProjectData("Orion Core", "Main platform."),
                List.of("Junior", "Pleno", "Sênior"),
                List.of(new OnboardOrganizationInput.RoleData("Desenvolvedor", "Desenvolvedor", List.of("DASHBOARD_INDIVIDUAL"))),
                invites,
                null
        );
    }

    @Test
    void shouldCreateAllEntitiesAndReturnOutput() {
        var savedOrg = new Organization("org-1", "Acme Corp", "acme-corp", "Tecnologia", "Brasil",
                List.of("Junior", "Pleno"), null, null, true, 0L, NOW, NOW);
        var customRole = new Role("role-dev", "org-1", "Desenvolvedor", "Desenvolvedor", false, null,
                List.of("DASHBOARD_INDIVIDUAL"), true, 0L, NOW, NOW);
        var adminRole = new Role("role-admin", null, "ADMIN", "ADMIN", true, null,
                List.of("USER_MANAGE", "ROLE_MANAGE"), true, 0L, NOW, NOW);
        var adminUser = new User("user-1", "org-1", "admin@acme.com", "hashed", "Maria Admin",
                "role-admin", null, null, null, null, null, true, true, 0L, NOW, NOW);
        var savedProject = new Project("proj-1", "org-1", "Orion Core", "orion-core", "Main platform.",
                true, null, 0L, NOW, NOW);

        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(savedOrg);
        when(roleRepository.save(any())).thenReturn(customRole);
        when(roleRepository.findGlobalByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordHasher.hash("Secure@1")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(adminUser);
        when(projectRepository.save(any())).thenReturn(savedProject);

        OnboardOrganizationOutput output = useCase.execute(buildInput(List.of()));

        assertThat(output.orgId()).isEqualTo("org-1");
        assertThat(output.orgSlug()).isEqualTo("acme-corp");
        assertThat(output.projectId()).isEqualTo("proj-1");
        assertThat(output.projectName()).isEqualTo("Orion Core");
        assertThat(output.inviteCount()).isZero();
        assertThat(output.permissionCodes()).containsExactlyInAnyOrder("USER_MANAGE", "ROLE_MANAGE");
        verify(organizationRepository).save(any(Organization.class));
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void should_addProjectAccessAllAndProjectManage_onlyToWizardRolesHoldingUserManage() {
        var savedOrg = new Organization("org-1", "Acme Corp", "acme-corp", "Tecnologia", "Brasil",
                List.of("Junior"), null, null, true, 0L, NOW, NOW);
        var adminRole = new Role("role-admin", null, "ADMIN", "ADMIN", true, null,
                List.of("USER_MANAGE", "PROJECT_ACCESS_ALL"), true, 0L, NOW, NOW);
        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(savedOrg);
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findGlobalByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordHasher.hash("Secure@1")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(new User("user-1", "org-1", "admin@acme.com", "hashed",
                "Maria Admin", "role-admin", null, null, null, null, null, true, true, 0L, NOW, NOW));
        when(projectRepository.save(any())).thenReturn(new Project("proj-1", "org-1", "Orion Core", "orion-core",
                "Main platform.", true, null, 0L, NOW, NOW));
        var input = new OnboardOrganizationInput(
                new OnboardOrganizationInput.OrgData("Acme Corp", "Tecnologia", "Brasil"),
                new OnboardOrganizationInput.AdminData("admin@acme.com", "Maria Admin", "Secure@1"),
                new OnboardOrganizationInput.ProjectData("Orion Core", "Main platform."),
                List.of("Junior"),
                List.of(new OnboardOrganizationInput.RoleData("Coordenador", "Coordenador", List.of("USER_MANAGE")),
                        new OnboardOrganizationInput.RoleData("Desenvolvedor", "Desenvolvedor",
                                List.of("DASHBOARD_INDIVIDUAL"))),
                List.of(),
                null);

        useCase.execute(input);

        org.mockito.ArgumentCaptor<Role> roles = org.mockito.ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, org.mockito.Mockito.times(2)).save(roles.capture());
        assertThat(roles.getAllValues().get(0).getPermissionCodes())
                .containsExactly("USER_MANAGE", "PROJECT_ACCESS_ALL", "PROJECT_MANAGE");
        assertThat(roles.getAllValues().get(1).getPermissionCodes()).containsExactly("DASHBOARD_INDIVIDUAL");
    }

    @Test
    void shouldThrowConflictWhenOrgSlugAlreadyExists() {
        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(buildInput(List.of())))
                .isInstanceOf(OrganizationSlugConflictException.class)
                .hasMessageContaining("acme-corp");
    }

    @Test
    void shouldThrowWhenAdminRoleSeedIsMissing() {
        var savedOrg = new Organization("org-1", "Acme Corp", "acme-corp", "Tecnologia", "Brasil",
                List.of(), null, null, true, 0L, NOW, NOW);
        var customRole = new Role("role-dev", "org-1", "Desenvolvedor", "Desenvolvedor", false, null,
                List.of("DASHBOARD_INDIVIDUAL"), true, 0L, NOW, NOW);

        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(savedOrg);
        when(roleRepository.save(any())).thenReturn(customRole);
        when(roleRepository.findGlobalByName("ADMIN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(buildInput(List.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ADMIN role not found");
    }

    @Test
    void shouldThrowWhenInviteReferencesUnknownRole() {
        var savedOrg = new Organization("org-1", "Acme Corp", "acme-corp", "Tecnologia", "Brasil",
                List.of(), null, null, true, 0L, NOW, NOW);
        var customRole = new Role("role-dev", "org-1", "Desenvolvedor", "Desenvolvedor", false, null,
                List.of("DASHBOARD_INDIVIDUAL"), true, 0L, NOW, NOW);
        var adminRole = new Role("role-admin", null, "ADMIN", "ADMIN", true, null,
                List.of("USER_MANAGE"), true, 0L, NOW, NOW);
        var adminUser = new User("user-1", "org-1", "admin@acme.com", "hashed", "Maria Admin",
                "role-admin", null, null, null, null, null, true, true, 0L, NOW, NOW);
        var savedProject = new Project("proj-1", "org-1", "Orion Core", "orion-core", "Main platform.",
                true, null, 0L, NOW, NOW);

        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(savedOrg);
        when(roleRepository.save(any())).thenReturn(customRole);
        when(roleRepository.findGlobalByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordHasher.hash(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(adminUser);
        when(projectRepository.save(any())).thenReturn(savedProject);

        var invite = new OnboardOrganizationInput.InviteData("dev@acme.com", "RoleInexistente", "Pleno");
        assertThatThrownBy(() -> useCase.execute(buildInput(List.of(invite))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("RoleInexistente");
    }

    @Test
    void shouldCreateInviteUsersWhenInvitesProvided() {
        var savedOrg = new Organization("org-1", "Acme Corp", "acme-corp", "Tecnologia", "Brasil",
                List.of(), null, null, true, 0L, NOW, NOW);
        var customRole = new Role("role-dev", "org-1", "Desenvolvedor", "Desenvolvedor", false, null,
                List.of("DASHBOARD_INDIVIDUAL"), true, 0L, NOW, NOW);
        var adminRole = new Role("role-admin", null, "ADMIN", "ADMIN", true, null,
                List.of("USER_MANAGE"), true, 0L, NOW, NOW);
        var adminUser = new User("user-1", "org-1", "admin@acme.com", "hashed", "Maria Admin",
                "role-admin", null, null, null, null, null, true, true, 0L, NOW, NOW);
        var savedProject = new Project("proj-1", "org-1", "Orion Core", "orion-core", "Main platform.",
                true, null, 0L, NOW, NOW);

        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(savedOrg);
        when(roleRepository.save(any())).thenReturn(customRole);
        when(roleRepository.findGlobalByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordHasher.hash(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(adminUser);
        when(projectRepository.save(any())).thenReturn(savedProject);

        var invite1 = new OnboardOrganizationInput.InviteData("dev1@acme.com", "Desenvolvedor", "Pleno");
        var invite2 = new OnboardOrganizationInput.InviteData("dev2@acme.com", "Desenvolvedor", "Junior");

        OnboardOrganizationOutput output = useCase.execute(buildInput(List.of(invite1, invite2)));

        assertThat(output.inviteCount()).isEqualTo(2);
        // 1 admin + 2 invites = 3 saves
        verify(userRepository, times(3)).save(any(User.class));
    }

    private void stubHappyPath() {
        var savedOrg = new Organization("org-1", "Acme Corp", "acme-corp", "Tecnologia", "Brasil",
                List.of("Junior"), null, null, true, 0L, NOW, NOW);
        var adminRole = new Role("role-admin", null, "ADMIN", "ADMIN", true, null,
                List.of("USER_MANAGE"), true, 0L, NOW, NOW);
        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(false);
        when(organizationRepository.save(any())).thenReturn(savedOrg);
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findGlobalByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordHasher.hash("Secure@1")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(new User("user-1", "org-1", "admin@acme.com", "hashed",
                "Maria Admin", "role-admin", null, null, null, null, null, true, true, 0L, NOW, NOW));
        when(projectRepository.save(any())).thenReturn(new Project("proj-1", "org-1", "Orion Core", "orion-core",
                "Main platform.", true, null, 0L, NOW, NOW));
    }

    @Test
    void should_recordASignupLoginForTheNewAdmin_inTheNewOrganization() {
        stubHappyPath();

        useCase.execute(buildInput(List.of()));

        verify(recordLoginUseCase).execute("user-1", "org-1", andromeda.sei.ms.feature.usage.domain.LoginMethod.SIGNUP);
    }

    @Test
    void should_notRecordLogin_when_theSignupFails() {
        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(buildInput(List.of())))
                .isInstanceOf(OrganizationSlugConflictException.class);
        org.mockito.Mockito.verifyNoInteractions(recordLoginUseCase);
    }

    @Test
    void should_returnTheOutput_evenWhenTheRecorderThrows() {
        stubHappyPath();
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(recordLoginUseCase)
                .execute(any(), any(), any());

        OnboardOrganizationOutput output = useCase.execute(buildInput(List.of()));

        assertThat(output.orgId()).isEqualTo("org-1");
    }

    private static final String FUNNEL = "3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b";

    private OnboardOrganizationInput withFunnel(String funnelId) {
        var in = buildInput(List.of());
        return new OnboardOrganizationInput(in.org(), in.admin(), in.project(), in.seniorities(), in.roles(),
                in.invites(), funnelId);
    }

    @Test
    void should_linkTheSignupFunnelToTheNewOrgAndAdmin_afterSuccess() {
        stubHappyPath();

        useCase.execute(withFunnel(FUNNEL));

        verify(linkFunnelUseCase).execute(FUNNEL, andromeda.sei.ms.feature.usage.domain.FunnelFlow.SIGNUP,
                "org-1", "user-1");
    }

    @Test
    void should_notLinkAnything_when_theSignupFails() {
        when(organizationRepository.existsBySlug("acme-corp")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(withFunnel(FUNNEL)))
                .isInstanceOf(OrganizationSlugConflictException.class);
        org.mockito.Mockito.verifyNoInteractions(linkFunnelUseCase);
    }

    @Test
    void should_returnTheOutput_evenWhenTheFunnelLinkThrows() {
        stubHappyPath();
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(linkFunnelUseCase)
                .execute(any(), any(), any(), any());

        OnboardOrganizationOutput output = useCase.execute(withFunnel(FUNNEL));

        assertThat(output.orgId()).isEqualTo("org-1");
    }
}
