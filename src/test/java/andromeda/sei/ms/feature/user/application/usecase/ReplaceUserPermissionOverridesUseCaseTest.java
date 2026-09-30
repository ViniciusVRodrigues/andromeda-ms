package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.application.dto.PermissionOverrideInput;
import andromeda.sei.ms.feature.user.application.dto.ReplacePermissionOverridesInput;
import andromeda.sei.ms.feature.user.application.dto.UserPermissionsOutput;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.PermissionCodeInvalidException;
import andromeda.sei.ms.feature.user.domain.exception.PermissionEscalationDeniedException;
import andromeda.sei.ms.feature.user.domain.exception.PermissionSelfEditDeniedException;
import andromeda.sei.ms.feature.user.domain.exception.PermissionNotProjectScopableException;
import andromeda.sei.ms.feature.user.domain.exception.UserConflictException;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.shared.application.security.UserPermissionsChanged;
import andromeda.sei.ms.shared.domain.security.PermissionEffect;
import andromeda.sei.ms.shared.domain.security.PermissionOverride;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** project-scoped-permissions 5.2/5.4 — validation, escalation guard and optimistic locking. */
@ExtendWith(MockitoExtension.class)
class ReplaceUserPermissionOverridesUseCaseTest {

    private static final String ORG = "org-1";
    private static final String ADMIN = "admin-1";
    private static final String TARGET = "dev-1";
    private static final String PROJECT_A = "project-a";
    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private GetUserPermissionsUseCase getUserPermissionsUseCase;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private ReplaceUserPermissionOverridesUseCase useCase;

    private User target;
    private final UserPermissionsOutput output = new UserPermissionsOutput(TARGET, "r", "DEVELOPER", List.of(),
            List.of(), List.of(), java.util.Map.of(), 4L);

    @BeforeEach
    void setUp() {
        target = new User(TARGET, ORG, "dev@x.com", null, "Dev", "role-dev", null, null, null, null, null,
                true, true, 3L, EARLIER, EARLIER);
        lenient().when(userRepository.findByIdAndOrganizationId(TARGET, ORG)).thenReturn(Optional.of(target));
        lenient().when(projectRepository.findActiveByIdAndOrganizationId(PROJECT_A, ORG))
                .thenReturn(Optional.of(new Project(PROJECT_A, ORG, "A", "a", null, true, List.of(), 1L, EARLIER, EARLIER)));
        lenient().when(getUserPermissionsUseCase.execute(ORG, TARGET)).thenReturn(output);
    }

    @Test
    void should_saveStampAndPublish_when_adminGrantsCodeHeHolds() {
        UserPermissionsOutput result = useCase.execute(input(admin(), 3L, grant("DASHBOARD_TEAM", PROJECT_A)));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        PermissionOverride stored = saved.getValue().getPermissionOverrides().getFirst();
        assertThat(stored.code()).isEqualTo("DASHBOARD_TEAM");
        assertThat(stored.projectId()).isEqualTo(PROJECT_A);
        assertThat(stored.updatedBy()).isEqualTo(ADMIN);
        verify(eventPublisher).publishEvent(new UserPermissionsChanged(ORG, TARGET));
        assertThat(result).isSameAs(output);
    }

    @Test
    void should_keepAuditOfUnchangedEntries() {
        PermissionOverride existing = new PermissionOverride("AZURE_READ", PermissionEffect.DENY, null, "someone", EARLIER);
        target.setPermissionOverrides(List.of(existing));

        useCase.execute(input(admin(), 3L, new PermissionOverrideInput("AZURE_READ", "DENY", null),
                grant("REPOS_READ", null)));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPermissionOverrides()).first().isEqualTo(existing);
        assertThat(saved.getValue().getPermissionOverrides().get(1).updatedBy()).isEqualTo(ADMIN);
    }

    @Test
    void should_return422NotProjectScopable_when_orgOnlyCodeOnProject() {
        assertThatThrownBy(() -> useCase.execute(input(admin(), 3L, grant("USER_MANAGE", PROJECT_A))))
                .isInstanceOf(PermissionNotProjectScopableException.class)
                .hasFieldOrPropertyWithValue("code", "PERMISSION_NOT_PROJECT_SCOPABLE");
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_denyEscalation_when_adminLacksTheCode() {
        UserPermissionSnapshot adminWithoutPricing = snapshot(List.of("USER_MANAGE", "PROJECT_ACCESS_ALL"));

        assertThatThrownBy(() -> useCase.execute(input(adminWithoutPricing, 3L, grant("PRICE_TABLE_MANAGE", null))))
                .isInstanceOf(PermissionEscalationDeniedException.class)
                .hasFieldOrPropertyWithValue("code", "PERMISSION_ESCALATION_DENIED");
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_denyEscalation_when_removingOverrideOfCodeTheCallerLacksAtThatScope() {
        target.setPermissionOverrides(List.of(
                new PermissionOverride("DASHBOARD_TEAM", PermissionEffect.GRANT, PROJECT_A, "root", EARLIER)));
        UserPermissionSnapshot managerNotInA = snapshot(List.of("USER_MANAGE", "DASHBOARD_TEAM"));

        assertThatThrownBy(() -> useCase.execute(input(managerNotInA, 3L)))
                .isInstanceOf(PermissionEscalationDeniedException.class);
    }

    @Test
    void should_return409_when_versionIsStale() {
        assertThatThrownBy(() -> useCase.execute(input(admin(), 2L, grant("REPOS_READ", null))))
                .isInstanceOf(UserConflictException.class)
                .hasFieldOrPropertyWithValue("code", "USER_CONFLICT");
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_return422_when_codeIsUnknownOrEffectInvalid() {
        assertThatThrownBy(() -> useCase.execute(input(admin(), 3L, grant("NOPE", null))))
                .isInstanceOf(PermissionCodeInvalidException.class);
        assertThatThrownBy(() -> useCase.execute(input(admin(), 3L,
                new PermissionOverrideInput("REPOS_READ", "MAYBE", null))))
                .isInstanceOf(PermissionCodeInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "PERMISSION_CODE_INVALID");
    }

    @Test
    void should_return404_when_projectIsOfAnotherOrganization() {
        when(projectRepository.findActiveByIdAndOrganizationId("foreign", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(input(admin(), 3L, grant("REPOS_READ", "foreign"))))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_return404_when_userIsOfAnotherOrganization() {
        when(userRepository.findByIdAndOrganizationId("stranger", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ReplacePermissionOverridesInput(admin(), "stranger", List.of(), null)))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void should_notSave_when_nothingChanged() {
        useCase.execute(input(admin(), null));

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void should_denySelfEdit_when_callerTargetsThemselves() {
        User self = new User(ADMIN, ORG, "admin@x.com", null, "Admin", "role-admin", null, null, null, null, null,
                true, true, 3L, EARLIER, EARLIER);
        when(userRepository.findByIdAndOrganizationId(ADMIN, ORG)).thenReturn(Optional.of(self));
        ReplacePermissionOverridesInput selfDeny = new ReplacePermissionOverridesInput(admin(), ADMIN,
                List.of(new PermissionOverrideInput("USER_MANAGE", "DENY", null)), 3L);

        assertThatThrownBy(() -> useCase.execute(selfDeny))
                .isInstanceOf(PermissionSelfEditDeniedException.class)
                .hasFieldOrPropertyWithValue("code", "PERMISSION_SELF_EDIT_DENIED");
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static UserPermissionSnapshot admin() {
        return snapshot(List.of("USER_MANAGE", "PROJECT_ACCESS_ALL", "DASHBOARD_TEAM", "REPOS_READ", "AZURE_READ",
                "PRICE_TABLE_MANAGE"));
    }

    private static UserPermissionSnapshot snapshot(List<String> roleCodes) {
        return new UserPermissionSnapshot(ORG, ADMIN, "role-admin", "ADMIN", roleCodes, List.of(), Set.of(), 1L);
    }

    private static PermissionOverrideInput grant(String code, String projectId) {
        return new PermissionOverrideInput(code, "GRANT", projectId);
    }

    private static ReplacePermissionOverridesInput input(UserPermissionSnapshot caller, Long version,
                                                         PermissionOverrideInput... overrides) {
        return new ReplacePermissionOverridesInput(caller, TARGET, List.of(overrides), version);
    }
}
