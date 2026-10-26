package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.application.port.PermissionSnapshotCache;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import andromeda.sei.ms.shared.domain.security.PermissionEffect;
import andromeda.sei.ms.shared.domain.security.PermissionOverride;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * project-scoped-permissions 7.1/7.4 — {@code GET /v1/projects} by accessible projects (not
 * {@code USER_MANAGE}): member, non-member, project grant and {@code PROJECT_ACCESS_ALL}.
 */
@ExtendWith(MockitoExtension.class)
class ListProjectsUseCaseTest {

    private static final String ORG = "org-1";
    private static final Instant T = Instant.parse("2026-09-01T00:00:00Z");

    @Mock private ProjectRepository projectRepository;
    @Mock private PermissionSnapshotCache cache;

    private ListProjectsUseCase useCase;
    private final Project a = project("A");
    private final Project b = project("B");

    @BeforeEach
    void setUp() {
        EffectivePermissionService service = new EffectivePermissionService(null, null, projectRepository, null, cache);
        useCase = new ListProjectsUseCase(service);
        lenient().when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(a, b));
    }

    @Test
    void should_listAllActiveProjects_when_callerHoldsAccessAll() {
        assertThat(useCase.execute(caller(List.of("PROJECT_ACCESS_ALL"), List.of(), Set.of()), null))
                .containsExactly(a, b);
    }

    @Test
    void should_listOnlyMemberProjects_when_callerLacksAccessAll_evenWithUserManage() {
        assertThat(useCase.execute(caller(List.of("USER_MANAGE"), List.of(), Set.of("B")), null))
                .containsExactly(b);
    }

    @Test
    void should_includeProjectsWithProjectLevelGrant() {
        List<PermissionOverride> grant = List.of(
                new PermissionOverride("REPOS_READ", PermissionEffect.GRANT, "A", "admin", T));

        assertThat(useCase.execute(caller(List.of(), grant, Set.of()), null)).containsExactly(a);
    }

    @Test
    void should_returnEmpty_when_noMembershipNoGrantNoAccessAll() {
        assertThat(useCase.execute(caller(List.of("DASHBOARD_TEAM"), List.of(), Set.of()), null)).isEmpty();
    }

    @Test
    void should_narrowByPermissionCode_forDashboardSelectors() {
        List<PermissionOverride> denyOnB = List.of(
                new PermissionOverride("DASHBOARD_TEAM", PermissionEffect.DENY, "B", "admin", T));

        assertThat(useCase.execute(caller(List.of("DASHBOARD_TEAM"), denyOnB, Set.of("A", "B")), "DASHBOARD_TEAM"))
                .containsExactly(a);
    }

    @Test
    void should_reject_unknownPermissionCode() {
        assertThatThrownBy(() -> useCase.execute(caller(List.of(), List.of(), Set.of()), "NOPE"))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PERMISSION_CODE_INVALID");
    }

    private static UserPermissionSnapshot caller(List<String> roleCodes, List<PermissionOverride> overrides,
                                                 Set<String> members) {
        return new UserPermissionSnapshot(ORG, "user-1", "r", "ROLE", roleCodes, overrides, members, 1L);
    }

    private static Project project(String id) {
        return new Project(id, ORG, id, id.toLowerCase(), null, true, List.of(), 1L, T, T);
    }
}
