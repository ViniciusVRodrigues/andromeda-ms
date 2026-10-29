package andromeda.sei.ms.feature.aiusage.infrastructure.migration;

import andromeda.sei.ms.feature.aiusage.application.usecase.RecalculateAiCostsUseCase;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCostAgentCacheMigrationTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private OrganizationRepository organizations;
    @Mock private RecalculateAiCostsUseCase recalculate;
    @Mock private ApplicationArguments args;

    private AiCostAgentCacheMigration migration;

    @BeforeEach
    void setUp() {
        migration = new AiCostAgentCacheMigration(mongoTemplate, organizations, recalculate);
    }

    private void noMarker(String... orgIds) {
        when(mongoTemplate.exists(any(Query.class), eq("schema_migrations"))).thenReturn(false);
        when(mongoTemplate.findDistinct(any(Query.class), eq("organizationId"), eq("ai_usages"), eq(String.class)))
                .thenReturn(List.of(orgIds));
    }

    private Organization org(String id, AiPriceEntry... entries) {
        Organization org = new Organization(id, "n", "s", null, null, List.of(), List.of(entries), null, true, 1L, null, null);
        when(organizations.findById(id)).thenReturn(Optional.of(org));
        return org;
    }

    @Test
    void should_skipEverything_whenMarkerPresent() {
        when(mongoTemplate.exists(any(Query.class), eq("schema_migrations"))).thenReturn(true);

        migration.run(args);

        verify(recalculate, never()).execute(anyString());
        verify(organizations, never()).save(any());
        verify(mongoTemplate, never()).save(any(Document.class), eq("schema_migrations"));
    }

    @Test
    void should_fillOnlyNullPrices_keepTypedValues_andNotAddModels() {
        noMarker("org-1");
        Organization o = org("org-1",
                new AiPriceEntry("claude-opus-5", new BigDecimal("0.005"), new BigDecimal("0.025"),
                        new BigDecimal("0.001"), null),
                new AiPriceEntry("modelo-custom", null, new BigDecimal("0.01")));
        when(recalculate.execute("org-1")).thenReturn(5);

        migration.run(args);

        assertThat(o.getAiPrices()).hasSize(2);
        AiPriceEntry opus = o.getAiPrices().get(0);
        assertThat(opus.getPricePerKInput()).isEqualByComparingTo("0.005");
        assertThat(opus.getPricePerKCacheRead()).isEqualByComparingTo("0.001");
        assertThat(opus.getPricePerKCacheWrite()).isEqualByComparingTo("0.00625");
        AiPriceEntry custom = o.getAiPrices().get(1);
        assertThat(custom.getPricePerKInput()).isNull();
        assertThat(custom.getPricePerKCacheRead()).isNull();
        verify(organizations).save(o);

        ArgumentCaptor<Document> marker = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).save(marker.capture(), eq("schema_migrations"));
        assertThat(marker.getValue().get("_id")).isEqualTo("ai-cost-agent-cache-2026-10");
        assertThat(marker.getValue().get("priceFieldsFilled")).isEqualTo(1L);
        assertThat(marker.getValue().get("recordsChanged")).isEqualTo(5L);
        assertThat(marker.getValue().get("organizations")).isEqualTo(1);
    }

    @Test
    void should_matchNormalizedNames_andFillZeroWriteForOpenAi() {
        noMarker("org-1");
        Organization o = org("org-1", new AiPriceEntry("gpt-5.5-2026-09-01", null, new BigDecimal("0.03")));

        migration.run(args);

        AiPriceEntry e = o.getAiPrices().get(0);
        assertThat(e.getPricePerKInput()).isEqualByComparingTo("0.005");
        assertThat(e.getPricePerKCacheRead()).isEqualByComparingTo("0.0005");
        assertThat(e.getPricePerKCacheWrite()).isEqualByComparingTo("0");
        assertThat(e.getPricePerKCacheWrite()).isNotNull();
    }

    @Test
    void should_notSaveTheOrg_whenNothingChanged_butStillRecalculate() {
        noMarker("org-1");
        org("org-1", new AiPriceEntry("claude-opus-5", new BigDecimal("0.005"), new BigDecimal("0.025"),
                new BigDecimal("0.0005"), new BigDecimal("0.00625")));

        migration.run(args);

        verify(organizations, never()).save(any());
        verify(recalculate).execute("org-1");
    }

    @Test
    void should_continueAfterAFailure_andNotWriteMarker() {
        noMarker("org-1", "org-2");
        org("org-1");
        org("org-2");
        when(recalculate.execute("org-1")).thenThrow(new IllegalStateException("boom"));

        migration.run(args);

        verify(recalculate).execute("org-2");
        verify(mongoTemplate, never()).save(any(Document.class), eq("schema_migrations"));
    }

    @Test
    void should_changeNothingOnTheSecondRun() {
        noMarker("org-1");
        Organization o = org("org-1", new AiPriceEntry("claude-opus-5", null, new BigDecimal("0.025")));

        migration.run(args);
        migration.run(args);

        // first run saved; second finds nothing null and does not save again
        verify(organizations, times(1)).save(o);
    }
}
