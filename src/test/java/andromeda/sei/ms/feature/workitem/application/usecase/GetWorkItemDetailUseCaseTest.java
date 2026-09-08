package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.azure.domain.*;
import andromeda.sei.ms.feature.workitem.domain.*;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetWorkItemDetailUseCaseTest {
    @Mock WorkItemRepository repository;
    @Mock AzureProjectBindingRepository bindings;
    @InjectMocks GetWorkItemDetailUseCase useCase;

    @Test
    void shouldUseTenantScopedLookupAndHideMissingItem() {
        when(repository.findByIdAndOrganizationIdAndActiveTrue("foreign", "org-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute("org-1", "foreign")).isInstanceOf(WorkItemNotFoundException.class);
        verify(repository, never()).findById(any());
        verifyNoInteractions(bindings);
    }

    @Test
    void shouldExposeExactGlobalFieldMappingForItemType() {
        var item = mock(WorkItem.class);
        when(item.getProjectId()).thenReturn("project-1");
        when(item.getRawType()).thenReturn("User Story");
        when(item.getEffort()).thenReturn(5.0);
        when(repository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(item));
        var binding = mock(AzureProjectBinding.class);
        var config = mock(WorkflowConfig.class);
        when(bindings.findByOrganizationIdAndProjectIdAndActiveTrue("org-1", "project-1")).thenReturn(Optional.of(binding));
        when(binding.getWorkflowConfig()).thenReturn(config);
        when(config.resolveFieldMapping(FieldMapping.InternalFieldTarget.COMPLEXITY, "User Story"))
                .thenReturn(Optional.empty());
        when(config.resolveFieldMapping(FieldMapping.InternalFieldTarget.EFFORT, "User Story"))
                .thenReturn(Optional.of(new FieldMapping(FieldMapping.InternalFieldTarget.EFFORT, "User Story", "Microsoft.VSTS.Scheduling.StoryPoints", "integer")));
        var result = useCase.execute("org-1", "wi-1");
        assertThat(result.effort().mappedField()).isEqualTo("Microsoft.VSTS.Scheduling.StoryPoints");
        assertThat(result.effort().fieldType()).isEqualTo("integer");
        assertThat(result.effort().value()).isEqualTo(5.0);
    }

    @Test
    void shouldMakePlanningContainersReadOnly() {
        var item = mock(WorkItem.class);
        when(item.getResolvedType()).thenReturn(WorkItem.ResolvedType.EPIC);
        when(repository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(item));
        assertThat(useCase.execute("org-1", "wi-1").editableType()).isFalse();
    }
}
