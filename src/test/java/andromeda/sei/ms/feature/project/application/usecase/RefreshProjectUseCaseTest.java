package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNoIntegrationBindingException;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshProjectUseCaseTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";

    @Mock private ProjectRepository projectRepository;
    @Mock private SyncTargetResolver syncTargetResolver;
    @Mock private SyncOrchestrator syncOrchestrator;

    @InjectMocks
    private RefreshProjectUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSubmitEveryBindingOfTheProjectAndReturnSyncId() {
        List<SyncTarget> targets = List.of(
                new SyncTarget(Integration.AZURE_DEVOPS, "az-1", PROJECT),
                new SyncTarget(Integration.GITHUB, "gh-1", PROJECT));
        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.of(project()));
        when(syncTargetResolver.forProject(ORG, PROJECT)).thenReturn(targets);
        when(syncOrchestrator.submit(any())).thenReturn("sync-1");

        String syncId = useCase.execute(PROJECT);

        assertThat(syncId).isEqualTo("sync-1");
        ArgumentCaptor<SyncRequest> request = ArgumentCaptor.forClass(SyncRequest.class);
        verify(syncOrchestrator).submit(request.capture());
        assertThat(request.getValue().operation()).isEqualTo(SyncOperation.PROJECT_REFRESH);
        assertThat(request.getValue().trigger()).isEqualTo(SyncTrigger.MANUAL);
        assertThat(request.getValue().projectId()).isEqualTo(PROJECT);
        assertThat(request.getValue().targets()).isEqualTo(targets);
    }

    @Test
    void shouldThrowNotFoundAndStartNothingForProjectOutsideTheOrganization() {
        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(PROJECT)).isInstanceOf(ProjectNotFoundException.class);
        verifyNoInteractions(syncOrchestrator, syncTargetResolver);
    }

    @Test
    void shouldThrowConflictWhenProjectHasNoBinding() {
        when(projectRepository.findActiveByIdAndOrganizationId(PROJECT, ORG)).thenReturn(Optional.of(project()));
        when(syncTargetResolver.forProject(ORG, PROJECT)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(PROJECT))
                .isInstanceOf(ProjectNoIntegrationBindingException.class)
                .extracting("code").isEqualTo("PROJECT_NO_INTEGRATION_BINDING");
        verifyNoInteractions(syncOrchestrator);
    }

    private static Project project() {
        Instant now = Instant.now();
        return new Project(PROJECT, ORG, "Project", "project", null, true, List.of(), 1L, now, now);
    }
}
