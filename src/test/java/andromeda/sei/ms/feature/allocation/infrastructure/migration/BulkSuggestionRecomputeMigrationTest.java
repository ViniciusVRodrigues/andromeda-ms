package andromeda.sei.ms.feature.allocation.infrastructure.migration;

import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BulkSuggestionRecomputeMigrationTest {

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final AutoAssignAllocationUseCase autoAssign = mock(AutoAssignAllocationUseCase.class);
    private final BulkSuggestionRecomputeMigration migration =
            new BulkSuggestionRecomputeMigration(mongoTemplate, autoAssign);

    @Test
    void recomputesEachPendingAutoSuggestionWithMoreThanTenItems() {
        when(mongoTemplate.find(any(Query.class), eq(AiUsageAllocationDocument.class))).thenReturn(List.of(
                doc("org-1", "user-1", LocalDate.of(2026, 8, 30)),
                doc("org-2", "user-2", LocalDate.of(2026, 9, 1))));

        migration.run(null);

        verify(autoAssign).execute("org-1", "user-1", LocalDate.of(2026, 8, 30), true);
        verify(autoAssign).execute("org-2", "user-2", LocalDate.of(2026, 9, 1), true);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(AiUsageAllocationDocument.class));
        String filter = query.getValue().getQueryObject().toJson();
        assertThat(filter).contains("PENDING_VALIDATION").contains("AUTO").contains("splits.10");
    }

    @Test
    void doesNothingWhenNoSuggestionIsBulk() {
        when(mongoTemplate.find(any(Query.class), eq(AiUsageAllocationDocument.class))).thenReturn(List.of());

        migration.run(null);

        verifyNoInteractions(autoAssign);
    }

    @Test
    void oneFailingDayDoesNotStopTheOthersNorTheStartup() {
        LocalDate bad = LocalDate.of(2026, 8, 30);
        LocalDate good = LocalDate.of(2026, 8, 31);
        when(mongoTemplate.find(any(Query.class), eq(AiUsageAllocationDocument.class))).thenReturn(List.of(
                doc("org-1", "user-1", bad), doc("org-1", "user-1", good)));
        doThrow(new RuntimeException("boom")).when(autoAssign).execute("org-1", "user-1", bad, true);

        assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();

        verify(autoAssign).execute("org-1", "user-1", good, true);
    }

    private static AiUsageAllocationDocument doc(String org, String user, LocalDate day) {
        return AiUsageAllocationDocument.builder().organizationId(org).userId(user).day(day).build();
    }
}
