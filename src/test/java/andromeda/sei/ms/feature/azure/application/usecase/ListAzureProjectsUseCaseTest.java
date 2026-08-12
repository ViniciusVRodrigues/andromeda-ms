package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureProjectOutput;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureProjectDto;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAzureProjectsUseCaseTest {

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureDevOpsClient azureDevOpsClient;
    @Mock private AzureAuthStrategyResolver authStrategyResolver;
    @Mock private IntegrationSyncLogRepository syncLogRepository;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;

    @InjectMocks
    private ListAzureProjectsUseCase useCase;

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        lenient().when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.of(connection()));
        lenient().when(authStrategyResolver.resolve(any())).thenReturn("Bearer plain-access");
        lenient().when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldExcludeProjectsBoundToOtherProjects_andKeepOwn_when_forProjectIdProvided() {
        when(azureDevOpsClient.getProjects("https://dev.azure.com/org", "Bearer plain-access"))
                .thenReturn(List.of(
                        new AzureProjectDto("ado-bound-elsewhere", "Bound Elsewhere"),
                        new AzureProjectDto("ado-own", "Own Project"),
                        new AzureProjectDto("ado-free", "Free Project")));
        when(azureProjectBindingRepository.findAllByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(List.of(
                        binding("proj-OTHER", "ado-bound-elsewhere"),
                        binding("proj-1", "ado-own")));

        List<AzureProjectOutput> projects = useCase.execute("proj-1");

        assertThat(projects).extracting(AzureProjectOutput::azureProjectId)
                .containsExactly("ado-own", "ado-free");
    }

    @Test
    void shouldReturnUnfilteredList_when_forProjectIdAbsent() {
        // Regression: param absent → byte-for-byte today's behavior, no exclusion at all.
        when(azureDevOpsClient.getProjects("https://dev.azure.com/org", "Bearer plain-access"))
                .thenReturn(List.of(
                        new AzureProjectDto("ado-bound-elsewhere", "Bound Elsewhere"),
                        new AzureProjectDto("ado-free", "Free Project")));

        List<AzureProjectOutput> projects = useCase.execute(null);

        assertThat(projects).extracting(AzureProjectOutput::azureProjectId)
                .containsExactly("ado-bound-elsewhere", "ado-free");
    }

    @Test
    void shouldThrowConnectionNotFound_when_noActiveConnection() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(AzureConnectionNotFoundException.class);
    }

    private AzureConnection connection() {
        return new AzureConnection("conn-1", ORG, "https://dev.azure.com/org",
                "enc-access", null, NOW.plusSeconds(3600), "user-1",
                AzureConnectionStatus.CONNECTED, null, 0, null, null, true, NOW, NOW, null,
                AzureAuthMethod.PAT, "enc-pat");
    }

    private AzureProjectBinding binding(String projectId, String azureProjectId) {
        return new AzureProjectBinding("binding-" + azureProjectId, ORG, projectId, azureProjectId,
                "Project " + azureProjectId, true, null, null, true, NOW, NOW, 1L);
    }
}
