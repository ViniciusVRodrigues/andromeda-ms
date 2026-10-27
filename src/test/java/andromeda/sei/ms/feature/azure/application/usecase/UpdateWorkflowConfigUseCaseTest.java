package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.UpdateWorkflowConfigInput;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.azure.domain.exception.AzureHierarchyLevelDuplicateTypeException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureWorkflowConfigMappingsEmptyException;
import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.TypeMapping;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateWorkflowConfigUseCaseTest {

    @Mock
    private AzureProjectBindingRepository azureProjectBindingRepository;

    @Mock
    private TriggerAzureSyncUseCase triggerAzureSyncUseCase;

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

    @Test
    void shouldRejectEmptyStatusMappingsWhenCurrentIsNonEmpty() {
        AzureProjectBinding binding = bindingWithNonEmptyMappings();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));

        var input = new UpdateWorkflowConfigInput(List.of(),
                List.of(new UpdateWorkflowConfigInput.TypeMappingInput("Bug", WorkItem.ResolvedType.BUG)),
                List.of(), null, List.of());

        assertThatThrownBy(() -> useCase.execute("bind-1", input))
                .isInstanceOf(AzureWorkflowConfigMappingsEmptyException.class);

        verify(azureProjectBindingRepository, never()).save(any());
    }

    @Test
    void shouldRejectEmptyTypeMappingsWhenCurrentIsNonEmpty() {
        AzureProjectBinding binding = bindingWithNonEmptyMappings();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));

        var input = new UpdateWorkflowConfigInput(
                List.of(new UpdateWorkflowConfigInput.StatusMappingInput("Bug", "Active", WorkItem.ResolvedStatus.IN_PROGRESS)),
                List.of(), List.of(), null, List.of());

        assertThatThrownBy(() -> useCase.execute("bind-1", input))
                .isInstanceOf(AzureWorkflowConfigMappingsEmptyException.class);

        verify(azureProjectBindingRepository, never()).save(any());
    }

    @Test
    void shouldAcceptEmptyMappingsWhenCurrentMappingsAreAlsoEmpty() {
        AzureProjectBinding binding = binding();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var input = new UpdateWorkflowConfigInput(List.of(), List.of(), List.of(), null, List.of());

        AzureProjectBinding result = useCase.execute("bind-1", input);

        assertThat(result.getWorkflowConfig().getStatusMappings()).isEmpty();
        assertThat(result.getWorkflowConfig().getTypeMappings()).isEmpty();
    }

    @Test
    void shouldAcceptNonEmptyMappingsAndReplaceConfig() {
        AzureProjectBinding binding = bindingWithNonEmptyMappings();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var input = new UpdateWorkflowConfigInput(
                List.of(new UpdateWorkflowConfigInput.StatusMappingInput("Bug", "Resolved", WorkItem.ResolvedStatus.IN_PROGRESS)),
                List.of(new UpdateWorkflowConfigInput.TypeMappingInput("Bug", WorkItem.ResolvedType.BUG)),
                List.of(), null, List.of());

        AzureProjectBinding result = useCase.execute("bind-1", input);

        assertThat(result.getWorkflowConfig().getStatusMappings()).hasSize(1);
        assertThat(result.getWorkflowConfig().getStatusMappings().get(0).getRawStatus()).isEqualTo("Resolved");
        assertThat(result.getWorkflowConfig().getTypeMappings()).hasSize(1);
    }

    @Test
    void shouldEnableSyncAndTriggerScopedSyncOnFirstCompletion() {
        AzureProjectBinding binding = bindingGatedClosed();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var input = new UpdateWorkflowConfigInput(List.of(), List.of(), List.of(), null, List.of());

        AzureProjectBinding result = useCase.execute("bind-1", input);

        assertThat(result.isSyncEnabled()).isTrue();
        verify(triggerAzureSyncUseCase).execute("bind-1");
    }

    @Test
    void shouldNotRetriggerGateWhenSyncAlreadyEnabled() {
        AzureProjectBinding binding = binding();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var input = new UpdateWorkflowConfigInput(List.of(), List.of(), List.of(), null, List.of());

        AzureProjectBinding result = useCase.execute("bind-1", input);

        assertThat(result.isSyncEnabled()).isTrue();
        verify(triggerAzureSyncUseCase, never()).execute(any());
    }

    @Test
    void shouldNotFailSaveWhenScopedSyncThrows() {
        AzureProjectBinding binding = bindingGatedClosed();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("azure unavailable"))
                .when(triggerAzureSyncUseCase).execute("bind-1");

        var input = new UpdateWorkflowConfigInput(List.of(), List.of(), List.of(), null, List.of());

        AzureProjectBinding result = useCase.execute("bind-1", input);

        assertThat(result.isSyncEnabled()).isTrue();
    }

    @Test
    void shouldTriggerScopedResyncWhenEditingAlreadyCustomBinding() {
        AzureProjectBinding binding = bindingWithNonEmptyMappings();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var input = new UpdateWorkflowConfigInput(
                List.of(new UpdateWorkflowConfigInput.StatusMappingInput("Bug", "Resolved", WorkItem.ResolvedStatus.IN_PROGRESS)),
                List.of(new UpdateWorkflowConfigInput.TypeMappingInput("Bug", WorkItem.ResolvedType.BUG)),
                List.of(), null, List.of());

        AzureProjectBinding result = useCase.execute("bind-1", input);

        assertThat(result.getWorkflowConfig().getConfigSource()).isEqualTo(WorkflowConfig.ConfigSource.CUSTOM);
        verify(triggerAzureSyncUseCase).execute("bind-1");
    }

    @Test
    void shouldNotDoubleTriggerResyncOnFirstTimeCompletion() {
        // First-time completion: syncEnabled was false and config was DEFAULT before this save.
        // Only the existing first-completion path (azure-staged-sync-gating) should fire —
        // this edit-resync path must not also trigger, since configSource wasn't CUSTOM yet.
        AzureProjectBinding binding = bindingGatedClosed();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var input = new UpdateWorkflowConfigInput(List.of(), List.of(), List.of(), null, List.of());

        AzureProjectBinding result = useCase.execute("bind-1", input);

        assertThat(result.isSyncEnabled()).isTrue();
        verify(triggerAzureSyncUseCase, org.mockito.Mockito.times(1)).execute("bind-1");
    }

    private AzureProjectBinding binding() {
        return new AzureProjectBinding("bind-1", "org-1", "proj-1", "azure-1", "Azure Proj",
                true, null,
                new WorkflowConfig(List.of(), List.of(), List.of(), null, WorkflowConfig.ConfigSource.DEFAULT),
                true, NOW, NOW, null);
    }

    private AzureProjectBinding bindingGatedClosed() {
        return new AzureProjectBinding("bind-1", "org-1", "proj-1", "azure-1", "Azure Proj",
                false, null,
                new WorkflowConfig(List.of(), List.of(), List.of(), null, WorkflowConfig.ConfigSource.DEFAULT),
                true, NOW, NOW, null);
    }

    private AzureProjectBinding bindingWithNonEmptyMappings() {
        return new AzureProjectBinding("bind-1", "org-1", "proj-1", "azure-1", "Azure Proj",
                true, null,
                new WorkflowConfig(
                        List.of(new StatusMapping("Bug", "Active", WorkItem.ResolvedStatus.IN_PROGRESS)),
                        List.of(new TypeMapping("Bug", WorkItem.ResolvedType.BUG)),
                        List.of(), null, WorkflowConfig.ConfigSource.CUSTOM),
                true, NOW, NOW, null);
    }
    @Test
    void shouldStoreReceivesAiPerTypeAndTreatAbsentAsFalse() {
        AzureProjectBinding binding = bindingWithNonEmptyMappings();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureProjectBindingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var input = new UpdateWorkflowConfigInput(
                List.of(new UpdateWorkflowConfigInput.StatusMappingInput("Bug", "Active", WorkItem.ResolvedStatus.IN_PROGRESS)),
                List.of(new UpdateWorkflowConfigInput.TypeMappingInput("Bug", WorkItem.ResolvedType.BUG, true),
                        new UpdateWorkflowConfigInput.TypeMappingInput("Task", WorkItem.ResolvedType.TASK, null),
                        new UpdateWorkflowConfigInput.TypeMappingInput("Epic", WorkItem.ResolvedType.EPIC, false)),
                List.of(), null, List.of());

        AzureProjectBinding result = useCase.execute("bind-1", input);

        var types = result.getWorkflowConfig().getTypeMappings();
        assertThat(types).extracting(TypeMapping::getRawType, TypeMapping::isReceivesAi)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Bug", true),
                        org.assertj.core.groups.Tuple.tuple("Task", false),
                        org.assertj.core.groups.Tuple.tuple("Epic", false));
    }
}
