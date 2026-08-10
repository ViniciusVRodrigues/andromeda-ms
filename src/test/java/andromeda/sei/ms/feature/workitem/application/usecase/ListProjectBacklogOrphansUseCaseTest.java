package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.application.classifier.WorkItemHierarchyClassifier.Classification;
import andromeda.sei.ms.feature.workitem.domain.WorkItemPageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListProjectBacklogOrphansUseCaseTest {

    @Mock
    private ProjectBacklogClassificationService classificationService;

    @InjectMocks
    private ListProjectBacklogOrphansUseCase useCase;

    @Test
    void shouldDelegateToClassificationServiceWithOrphanClassification() {
        WorkItemPageResult expected = new WorkItemPageResult(List.of(), 0, 20, 0);
        when(classificationService.execute("proj-1", Classification.ORPHAN, 0, 20)).thenReturn(expected);

        WorkItemPageResult result = useCase.execute("proj-1", 0, 20);

        assertThat(result).isSameAs(expected);
        verify(classificationService).execute(eq("proj-1"), eq(Classification.ORPHAN), eq(0), eq(20));
    }
}
