package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.dto.AIUsageEntry;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpsertAIUsageUseCaseTest {

    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private AutoAssignAllocationUseCase autoAssign;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private Organization organization;

    private UpsertAIUsageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpsertAIUsageUseCase(aiUsageRepository, autoAssign, new CalculateAiCostUseCase(), organizationRepository,
                (o, u, d, e, m) -> andromeda.sei.ms.feature.aiusage.domain.AiefCoexistence.Verdict.STORE);
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
    }

    private AIUsageEntry entry(String model) {
        return new AIUsageEntry(null, "ds", LocalDate.of(2026, 10, 1), model, "ws", null, "m", null, 700L, 1000L, 1, null);
    }

    @Test
    void should_writeOutputOnlyCost_whenModelHasPrice() {
        when(organization.getAiPrices()).thenReturn(List.of(new AiPriceEntry("gpt-4o", BigDecimal.ONE, new BigDecimal("10"))));

        useCase.execute(List.of(entry("gpt-4o")), "org-1", "user-1");

        verify(aiUsageRepository).updateCost(any(), any(), any(), any(), eq("gpt-4o"), any(), any(), eq(new BigDecimal("10.000000")));
    }

    @Test
    void should_writeNullCost_whenModelLostItsPrice() {
        when(organization.getAiPrices()).thenReturn(List.of());

        useCase.execute(List.of(entry("gpt-4o")), "org-1", "user-1");

        verify(aiUsageRepository).updateCost(any(), any(), any(), any(), eq("gpt-4o"), any(), any(), eq((BigDecimal) null));
    }
}
