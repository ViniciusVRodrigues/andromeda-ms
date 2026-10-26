package andromeda.sei.ms.feature.integration.application.usecase;

import andromeda.sei.ms.feature.integration.application.dto.SyncLogOutput;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.shared.application.port.PermissionSnapshotCache;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/** project-scoped-permissions 7.3/7.4 — sync logs filtered by the projects the caller can read. */
@ExtendWith(MockitoExtension.class)
class GetIntegrationSyncLogsUseCaseTest {

    private static final String ORG = "org-1";
    private static final Instant T = Instant.parse("2026-09-01T00:00:00Z");

    @Mock private IntegrationSyncLogRepository syncLogRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private PermissionSnapshotCache cache;

    private GetIntegrationSyncLogsUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        useCase = new GetIntegrationSyncLogsUseCase(syncLogRepository,
                new EffectivePermissionService(null, null, projectRepository, null, cache));
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(project("A"), project("B")));
        when(syncLogRepository.findByOrganizationIdAndIntegration(eq(ORG), eq(Integration.AZURE_DEVOPS), isNull(), anyInt()))
                .thenReturn(List.of(log("l-a", "A"), log("l-b", "B"), log("l-org", null)));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_keepOnlyMemberProjectLogs_forMemberWithoutOrgWideAccess() {
        UserPermissionSnapshot member = caller(List.of("AZURE_READ"), Set.of("A"));

        assertThat(useCase.execute(Integration.AZURE_DEVOPS, null, member)).extracting(SyncLogOutput::id)
                .containsExactly("l-a", "l-org");
    }

    @Test
    void should_hideEverything_forNonMemberWithoutTheCode() {
        UserPermissionSnapshot stranger = caller(List.of("REPOS_READ"), Set.of("A"));

        assertThat(useCase.execute(Integration.AZURE_DEVOPS, null, stranger)).isEmpty();
    }

    @Test
    void should_keepEveryLog_forAccessAllHolder() {
        UserPermissionSnapshot admin = caller(List.of("AZURE_READ", "PROJECT_ACCESS_ALL"), Set.of());

        assertThat(useCase.execute(Integration.AZURE_DEVOPS, null, admin)).extracting(SyncLogOutput::id)
                .containsExactly("l-a", "l-b", "l-org");
    }

    private static UserPermissionSnapshot caller(List<String> roleCodes, Set<String> members) {
        return new UserPermissionSnapshot(ORG, "u", "r", "ROLE", roleCodes, List.of(), members, 1L);
    }

    private static Project project(String id) {
        return new Project(id, ORG, id, id.toLowerCase(), null, true, List.of(), 1L, T, T);
    }

    private static IntegrationSyncLog log(String id, String projectId) {
        return new IntegrationSyncLog(id, ORG, Integration.AZURE_DEVOPS, SyncOperation.SYNC_SPRINTS, SyncStatus.SUCCESS,
                SyncTrigger.SCHEDULED, projectId, T, T, 1L, 0, 0, null, null);
    }
}
