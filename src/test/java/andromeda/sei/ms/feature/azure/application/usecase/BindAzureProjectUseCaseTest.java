package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.BindAzureProjectInput;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureProjectAlreadyBoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoProjectCapabilitiesDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindAzureProjectUseCaseTest {

    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureDevOpsClient azureDevOpsClient;
    @Mock private AzureTokenRefreshService azureTokenRefreshService;
    @Mock private AzureAuthStrategyResolver authStrategyResolver;

    @InjectMocks
    private BindAzureProjectUseCase useCase;

    private static final Instant NOW = Instant.now();
    private AzureConnection connection;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        connection = new AzureConnection("conn-1", "org-1", "https://dev.azure.com/org",
                "enc-access", "enc-refresh", NOW.plusSeconds(3600),
                "user-1", AzureConnectionStatus.CONNECTED, null, 0, null, null, true, NOW, NOW, null,
                andromeda.sei.ms.feature.azure.domain.AzureAuthMethod.OAUTH, null);
        // lenient: the exclusivity-guard test throws before workflow detection resolves auth
        lenient().when(authStrategyResolver.resolve(any())).thenReturn("Bearer plain-access");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSeedWorkflowConfigOnCreate() {
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureDevOpsClient.getProjectCapabilities(anyString(), anyString(), anyString()))
                .thenReturn(new AdoProjectCapabilitiesDto("Scrum"));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AzureProjectBinding result = useCase.execute("proj-1",
                new BindAzureProjectInput("azure-proj-1", "My Project"));

        assertThat(result.getWorkflowConfig()).isNotNull();
        assertThat(result.getWorkflowConfig().getConfigSource()).isEqualTo(WorkflowConfig.ConfigSource.DEFAULT);
    }

    @Test
    void shouldCreateBindingWithSyncDisabled() {
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureDevOpsClient.getProjectCapabilities(anyString(), anyString(), anyString()))
                .thenReturn(new AdoProjectCapabilitiesDto("Scrum"));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AzureProjectBinding result = useCase.execute("proj-1",
                new BindAzureProjectInput("azure-proj-1", "My Project"));

        assertThat(result.isSyncEnabled()).isFalse();
    }

    @Test
    void shouldResetSyncDisabledOnRebind() {
        AzureProjectBinding existing = new AzureProjectBinding("bind-1", "org-1", "proj-1",
                "old-azure-id", "Old Project", true, null, null, true, NOW, NOW, null);
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(anyString(), anyString()))
                .thenReturn(Optional.of(existing));
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureDevOpsClient.getProjectCapabilities(anyString(), anyString(), anyString()))
                .thenReturn(new AdoProjectCapabilitiesDto("Agile"));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AzureProjectBinding result = useCase.execute("proj-1",
                new BindAzureProjectInput("azure-proj-2", "New Project"));

        assertThat(result.isSyncEnabled()).isFalse();
    }

    @Test
    void shouldReseedWorkflowConfigOnUpdate() {
        AzureProjectBinding existing = new AzureProjectBinding("bind-1", "org-1", "proj-1",
                "old-azure-id", "Old Project", true, null, null, true, NOW, NOW, null);
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(anyString(), anyString()))
                .thenReturn(Optional.of(existing));
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureDevOpsClient.getProjectCapabilities(anyString(), anyString(), anyString()))
                .thenReturn(new AdoProjectCapabilitiesDto("Agile"));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AzureProjectBinding result = useCase.execute("proj-1",
                new BindAzureProjectInput("azure-proj-2", "New Project"));

        assertThat(result.getWorkflowConfig()).isNotNull();
        assertThat(result.getAzureProjectId()).isEqualTo("azure-proj-2");
    }

    @Test
    void shouldUseFallbackWhenDetectionFails() {
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureDevOpsClient.getProjectCapabilities(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("ADO API unavailable"));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AzureProjectBinding result = useCase.execute("proj-1",
                new BindAzureProjectInput("azure-proj-1", "My Project"));

        assertThat(result.getWorkflowConfig()).isNotNull();
        assertThat(result.getWorkflowConfig().getConfigSource()).isEqualTo(WorkflowConfig.ConfigSource.DEFAULT);
    }

    // ── Exclusivity guard (design.md D3) ─────────────────────────────────

    @Test
    void shouldThrowAlreadyBound_when_azureProjectActivelyBoundToDifferentProject() {
        AzureProjectBinding otherProjectBinding = new AzureProjectBinding(
                "bind-other", "org-1", "proj-OTHER", "azure-proj-1", "My Project",
                true, null, null, true, NOW, NOW, null);
        when(azureProjectBindingRepository
                .findByOrganizationIdAndAzureProjectIdAndActiveTrue("org-1", "azure-proj-1"))
                .thenReturn(Optional.of(otherProjectBinding));

        assertThatThrownBy(() -> useCase.execute("proj-1",
                new BindAzureProjectInput("azure-proj-1", "My Project")))
                .isInstanceOf(AzureProjectAlreadyBoundException.class)
                .satisfies(ex -> assertThat(((AzureProjectAlreadyBoundException) ex).getCode())
                        .isEqualTo("AZURE_PROJECT_ALREADY_BOUND"));

        verify(azureProjectBindingRepository, never()).save(any());
    }

    @Test
    void shouldReplaceUnchanged_when_sameProjectRebindsTheSameAzureProject() {
        // The guard runs against the TARGET azureProjectId; a binding on the SAME project is
        // not a conflict and the replace behavior proceeds as before.
        AzureProjectBinding ownBinding = new AzureProjectBinding(
                "bind-1", "org-1", "proj-1", "azure-proj-1", "My Project",
                true, null, null, true, NOW, NOW, null);
        when(azureProjectBindingRepository
                .findByOrganizationIdAndAzureProjectIdAndActiveTrue("org-1", "azure-proj-1"))
                .thenReturn(Optional.of(ownBinding));
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(anyString(), anyString()))
                .thenReturn(Optional.of(ownBinding));
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureDevOpsClient.getProjectCapabilities(anyString(), anyString(), anyString()))
                .thenReturn(new AdoProjectCapabilitiesDto("Scrum"));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AzureProjectBinding result = useCase.execute("proj-1",
                new BindAzureProjectInput("azure-proj-1", "My Project"));

        assertThat(result.getId()).isEqualTo("bind-1");
        assertThat(result.getAzureProjectId()).isEqualTo("azure-proj-1");
    }

    @Test
    void shouldNotBlock_when_previousBindingIsInactive() {
        // The port method filters active=true, so an inactive binding never surfaces and the
        // ADO project is free to bind anywhere again.
        when(azureProjectBindingRepository
                .findByOrganizationIdAndAzureProjectIdAndActiveTrue("org-1", "azure-proj-1"))
                .thenReturn(Optional.empty());
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureTokenRefreshService.refreshIfNeeded(connection)).thenReturn(connection);
        when(azureDevOpsClient.getProjectCapabilities(anyString(), anyString(), anyString()))
                .thenReturn(new AdoProjectCapabilitiesDto("Scrum"));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AzureProjectBinding result = useCase.execute("proj-2",
                new BindAzureProjectInput("azure-proj-1", "My Project"));

        assertThat(result.getProjectId()).isEqualTo("proj-2");
    }
}
