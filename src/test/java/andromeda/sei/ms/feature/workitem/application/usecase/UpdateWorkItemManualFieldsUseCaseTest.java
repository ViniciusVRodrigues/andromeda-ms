package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.application.dto.UpdateWorkItemManualFieldsInput;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateWorkItemManualFieldsUseCaseTest {

    @Mock
    private WorkItemRepository workItemRepository;

    @Mock
    private andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository bindingRepository;

    @InjectMocks
    private UpdateWorkItemManualFieldsUseCase useCase;

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
    void shouldSetComplexityAsManualOverride() {
        WorkItem existing = workItem("wi-1", "org-1");
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(existing));
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateWorkItemManualFieldsInput input = new UpdateWorkItemManualFieldsInput(
                true, 8.0, false, false, null, false);

        WorkItem result = useCase.execute("wi-1", "user-1", input);

        assertThat(result.getComplexity()).isEqualTo(8.0);
        assertThat(result.getComplexitySource()).isEqualTo(FieldValueSource.MANUAL_OVERRIDE);
        assertThat(result.getComplexityOverriddenBy()).isEqualTo("user-1");
        assertThat(result.getComplexityOverriddenAt()).isNotNull();
        // effort untouched
        assertThat(result.getEffort()).isNull();
        assertThat(result.getEffortSource()).isNull();
    }

    @Test
    void shouldSetEffortAsManualOverride() {
        WorkItem existing = workItem("wi-1", "org-1");
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(existing));
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateWorkItemManualFieldsInput input = new UpdateWorkItemManualFieldsInput(
                false, null, false, true, 13.0, false);

        WorkItem result = useCase.execute("wi-1", "user-2", input);

        assertThat(result.getEffort()).isEqualTo(13.0);
        assertThat(result.getEffortSource()).isEqualTo(FieldValueSource.MANUAL_OVERRIDE);
        assertThat(result.getEffortOverriddenBy()).isEqualTo("user-2");
        assertThat(result.getEffortOverriddenAt()).isNotNull();
    }

    @Test
    void shouldClearComplexityOverrideBackToAzureSync() {
        WorkItem existing = workItem("wi-1", "org-1");
        existing.setComplexity(8.0);
        existing.setComplexitySource(FieldValueSource.MANUAL_OVERRIDE);
        existing.setComplexityOverriddenBy("user-1");
        existing.setComplexityOverriddenAt(NOW);
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(existing));
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateWorkItemManualFieldsInput input = new UpdateWorkItemManualFieldsInput(
                false, null, true, false, null, false);

        WorkItem result = useCase.execute("wi-1", "user-1", input);

        assertThat(result.getComplexitySource()).isEqualTo(FieldValueSource.AZURE_SYNC);
        assertThat(result.getComplexityOverriddenBy()).isNull();
        assertThat(result.getComplexityOverriddenAt()).isNull();
        // value itself is left as-is until next sync repopulates it, per spec
        assertThat(result.getComplexity()).isEqualTo(8.0);
    }

    @Test
    void shouldClearEffortOverrideBackToAzureSync() {
        WorkItem existing = workItem("wi-1", "org-1");
        existing.setEffort(13.0);
        existing.setEffortSource(FieldValueSource.MANUAL_OVERRIDE);
        existing.setEffortOverriddenBy("user-2");
        existing.setEffortOverriddenAt(NOW);
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(existing));
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateWorkItemManualFieldsInput input = new UpdateWorkItemManualFieldsInput(
                false, null, false, false, null, true);

        WorkItem result = useCase.execute("wi-1", "user-2", input);

        assertThat(result.getEffortSource()).isEqualTo(FieldValueSource.AZURE_SYNC);
        assertThat(result.getEffortOverriddenBy()).isNull();
        assertThat(result.getEffortOverriddenAt()).isNull();
        assertThat(result.getEffort()).isEqualTo(13.0);
    }

    @Test
    void shouldThrowWhenWorkItemNotFound() {
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("missing", "org-1")).thenReturn(Optional.empty());

        UpdateWorkItemManualFieldsInput input = new UpdateWorkItemManualFieldsInput(
                true, 5.0, false, false, null, false);

        assertThatThrownBy(() -> useCase.execute("missing", "user-1", input))
                .isInstanceOf(WorkItemNotFoundException.class);
    }

    @Test
    void shouldThrowWhenWorkItemBelongsToDifferentOrganization() {
        WorkItem otherOrgItem = workItem("wi-1", "org-2");
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(otherOrgItem));

        UpdateWorkItemManualFieldsInput input = new UpdateWorkItemManualFieldsInput(
                true, 5.0, false, false, null, false);

        assertThatThrownBy(() -> useCase.execute("wi-1", "user-1", input))
                .isInstanceOf(WorkItemNotFoundException.class);
    }

    @Test
    void shouldThrowWhenWorkItemIsInactive() {
        WorkItem inactive = workItem("wi-1", "org-1");
        inactive.setActive(false);
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(inactive));

        UpdateWorkItemManualFieldsInput input = new UpdateWorkItemManualFieldsInput(
                true, 5.0, false, false, null, false);

        assertThatThrownBy(() -> useCase.execute("wi-1", "user-1", input))
                .isInstanceOf(WorkItemNotFoundException.class);
    }

    @Test
    void shouldOnlyUpdatePresentFieldsLeavingOtherUntouched() {
        WorkItem existing = workItem("wi-1", "org-1");
        existing.setComplexity(3.0);
        existing.setComplexitySource(FieldValueSource.AZURE_SYNC);
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(existing));
        when(workItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateWorkItemManualFieldsInput input = new UpdateWorkItemManualFieldsInput(
                false, null, false, true, 21.0, false);

        ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
        useCase.execute("wi-1", "user-1", input);
        org.mockito.Mockito.verify(workItemRepository).save(captor.capture());

        assertThat(captor.getValue().getComplexity()).isEqualTo(3.0);
        assertThat(captor.getValue().getComplexitySource()).isEqualTo(FieldValueSource.AZURE_SYNC);
        assertThat(captor.getValue().getEffort()).isEqualTo(21.0);
        assertThat(captor.getValue().getEffortSource()).isEqualTo(FieldValueSource.MANUAL_OVERRIDE);
    }

    private WorkItem workItem(String id, String organizationId) {
        return new WorkItem(id, organizationId, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", NOW, "Title", "Bug", WorkItem.ResolvedType.BUG,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), null, null, null, null, null,
                NOW, null, null, null, null, null, true, null, NOW,
                null, null, null, null, null, null, null, null);
    }

    @Test
    void shouldRejectNegativeOrNonFiniteEffort() {
        var existing = workItem("wi-1", "org-1");
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", "org-1")).thenReturn(Optional.of(existing));
        for (Double invalid : List.of(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThatThrownBy(() -> useCase.execute("wi-1", "user-1",
                    new UpdateWorkItemManualFieldsInput(false, null, false, true, invalid, false)))
                    .isInstanceOf(andromeda.sei.ms.shared.domain.exception.BusinessRuleException.class);
        }
        org.mockito.Mockito.verify(workItemRepository, org.mockito.Mockito.never()).save(any());
    }
}
