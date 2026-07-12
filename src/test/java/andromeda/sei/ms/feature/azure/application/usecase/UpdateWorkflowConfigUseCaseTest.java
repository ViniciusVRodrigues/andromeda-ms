package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.UpdateWorkflowConfigInput;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureHierarchyLevelDuplicateTypeException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateWorkflowConfigUseCaseTest {

    @Mock
    private AzureProjectBindingRepository azureProjectBindingRepository;

    @InjectMocks
    private UpdateWorkflowConfigUseCase useCase;

    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSaveHierarchyLevels() {
        AzureProjectBinding binding = binding();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var input = new UpdateWorkflowConfigInput(List.of(), List.of(), List.of(), null,
                List.of(new UpdateWorkflowConfigInput.HierarchyLevelInput("Epic", 0),
                        new UpdateWorkflowConfigInput.HierarchyLevelInput("Task", 1)));

        AzureProjectBinding result = useCase.execute("bind-1", input);

        assertThat(result.getWorkflowConfig().getHierarchyLevels()).hasSize(2);
        assertThat(result.getWorkflowConfig().getHierarchyLevels().get(0).getRawType()).isEqualTo("Epic");
        assertThat(result.getWorkflowConfig().getHierarchyLevels().get(0).getRank()).isEqualTo(0);
    }

    @Test
    void shouldAcceptEmptyHierarchyLevels() {
        AzureProjectBinding binding = binding();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var input = new UpdateWorkflowConfigInput(List.of(), List.of(), List.of(), null, List.of());

        AzureProjectBinding result = useCase.execute("bind-1", input);

        assertThat(result.getWorkflowConfig().getHierarchyLevels()).isEmpty();
    }

    @Test
    void shouldRejectDuplicateRawTypeInHierarchyLevels() {
        AzureProjectBinding binding = binding();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));

        var input = new UpdateWorkflowConfigInput(List.of(), List.of(), List.of(), null,
                List.of(new UpdateWorkflowConfigInput.HierarchyLevelInput("Task", 0),
                        new UpdateWorkflowConfigInput.HierarchyLevelInput("Task", 1)));

        assertThatThrownBy(() -> useCase.execute("bind-1", input))
                .isInstanceOf(AzureHierarchyLevelDuplicateTypeException.class);
    }

    private AzureProjectBinding binding() {
        return new AzureProjectBinding("bind-1", "org-1", "proj-1", "azure-1", "Azure Proj",
                true, null,
                new WorkflowConfig(List.of(), List.of(), List.of(), null, WorkflowConfig.ConfigSource.DEFAULT),
                true, NOW, NOW, null);
    }
}
