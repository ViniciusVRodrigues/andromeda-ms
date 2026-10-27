package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecalculateAiCostsUseCaseTest {

    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private Organization organization;

    private RecalculateAiCostsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecalculateAiCostsUseCase(aiUsageRepository, organizationRepository, new CalculateAiCostUseCase());
    }

    private AIUsage usage(String model, long out, BigDecimal cost) {
        return new AIUsage(null, "org-1", "user-1", null, "ds-1", LocalDate.of(2026, 10, 1), model, "ws", null, "m", null,
                500L, out, 1, null, null, cost, Instant.now(), true, null, Instant.now(), Instant.now());
    }

    @Test
    void should_writeNewCostOrNull_andCountOnlyChanged() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(organization.getAiPrices()).thenReturn(List.of(new AiPriceEntry("a", BigDecimal.ONE, new BigDecimal("10"))));
        AIUsage same = usage("a", 1000L, new BigDecimal("10.000000"));
        AIUsage stale = usage("a", 2000L, new BigDecimal("9.0"));
        AIUsage lost = usage("b", 1000L, new BigDecimal("1.0"));
        AIUsage stillNull = usage("b", 1000L, null);
        when(aiUsageRepository.findAllByOrganizationId("org-1")).thenReturn(List.of(same, stale, lost, stillNull));

        int changed = useCase.execute("org-1");

        assertThat(changed).isEqualTo(2);
        verify(aiUsageRepository).updateCost(any(), any(), any(), any(), eq("a"), any(), any(), eq(new BigDecimal("20.000000")));
        verify(aiUsageRepository, org.mockito.Mockito.times(2))
                .updateCost(any(), any(), any(), any(), eq("b"), any(), any(), eq((BigDecimal) null));
    }

    @Test
    void should_clearAllCosts_when_priceTableIsEmpty() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(organization.getAiPrices()).thenReturn(List.of());
        when(aiUsageRepository.findAllByOrganizationId("org-1"))
                .thenReturn(List.of(usage("a", 1000L, new BigDecimal("1.0")), usage("a", 1L, null)));

        int changed = useCase.execute("org-1");

        assertThat(changed).isEqualTo(1);
        verify(aiUsageRepository, org.mockito.Mockito.times(2))
                .updateCost(any(), any(), any(), any(), any(), any(), any(), eq((BigDecimal) null));
        verify(aiUsageRepository, never()).upsert(any());
    }
}
