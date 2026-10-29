package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.dto.UnpricedModelOutput;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageModelOutput;
import andromeda.sei.ms.feature.aiusage.domain.exception.AiUsagePeriodInvalidException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUnpricedModelsUseCaseTest {

    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private Organization organization;

    private ListUnpricedModelsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListUnpricedModelsUseCase(aiUsageRepository, organizationRepository, new CalculateAiCostUseCase());
    }

    @Test
    void should_listOnlyUnpriced_sortedByOutputDesc_withBillableFlag() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(organization.getAiPrices()).thenReturn(List.of(
                new AiPriceEntry("claude-sonnet-4-5", null, new BigDecimal("15"))));
        when(aiUsageRepository.summarizeOutputByModel("org-1", null, null)).thenReturn(List.of(
                new AiUsageModelOutput("claude-sonnet-4-5-20250929", 9_999L, 3),
                new AiUsageModelOutput("gpt-6", 100L, 2),
                new AiUsageModelOutput("auto", 500L, 7),
                new AiUsageModelOutput("gemini-x", 900L, 1)));

        List<UnpricedModelOutput> result = useCase.execute("org-1", null, null);

        assertThat(result).containsExactly(
                new UnpricedModelOutput("gemini-x", 900L, 1, true, List.of("OUTPUT"), 0, 0, 0),
                new UnpricedModelOutput("gpt-6", 100L, 2, true, List.of("OUTPUT"), 0, 0, 0),
                new UnpricedModelOutput("auto", 500L, 7, false, List.of(), 0, 0, 0));
    }

    @Test
    void should_passTheOpenOrGivenPeriodThrough() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.empty());
        LocalDate from = LocalDate.of(2026, 9, 1);

        useCase.execute("org-1", from, null);

        verify(aiUsageRepository).summarizeOutputByModel("org-1", from, null);
    }

    @Test
    void should_reject_when_fromIsAfterTo() {
        assertThatThrownBy(() -> useCase.execute("org-1", LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(AiUsagePeriodInvalidException.class);
    }

    @Test
    void should_listIncompletePrice_withMissingPartsInOrder() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(organization.getAiPrices()).thenReturn(List.of(
                new AiPriceEntry("claude-opus-5", new BigDecimal("0.005"), new BigDecimal("0.025"))));
        when(aiUsageRepository.summarizeOutputByModel("org-1", null, null)).thenReturn(List.of(
                new AiUsageModelOutput("claude-opus-5", 5_000L, 4, 1_000L, 3_400_000L, 90_000L)));

        assertThat(useCase.execute("org-1", null, null)).containsExactly(
                new UnpricedModelOutput("claude-opus-5", 5_000L, 4, true,
                        List.of("CACHE_READ", "CACHE_WRITE"), 1_000L, 3_400_000L, 90_000L));
    }

    @Test
    void should_notFlagZeroPrice_orPartsTheAgentDidNotUse() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(organization.getAiPrices()).thenReturn(List.of(
                new AiPriceEntry("m1", new BigDecimal("0.005"), new BigDecimal("0.025"), new BigDecimal("0.0005"), BigDecimal.ZERO),
                new AiPriceEntry("m2", null, new BigDecimal("0.025"))));
        when(aiUsageRepository.summarizeOutputByModel("org-1", null, null)).thenReturn(List.of(
                new AiUsageModelOutput("m1", 10L, 1, 5L, 5L, 5L),
                new AiUsageModelOutput("m2", 10L, 1, 0L, 0L, 0L)));

        assertThat(useCase.execute("org-1", null, null)).isEmpty();
    }

    @Test
    void should_orderNoPrice_thenIncomplete_thenNonBillable_eachByOutputDesc() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(organization.getAiPrices()).thenReturn(List.of(
                new AiPriceEntry("a", new BigDecimal("1"), new BigDecimal("1"), null, null),
                new AiPriceEntry("b", new BigDecimal("1"), new BigDecimal("1"), null, null)));
        when(aiUsageRepository.summarizeOutputByModel("org-1", null, null)).thenReturn(List.of(
                new AiUsageModelOutput("auto", 9_000L, 1, 0, 0, 0),
                new AiUsageModelOutput("a", 100L, 1, 0, 1L, 0),
                new AiUsageModelOutput("b", 300L, 1, 0, 1L, 0),
                new AiUsageModelOutput("zzz", 10L, 1, 0, 0, 0),
                new AiUsageModelOutput("yyy", 20L, 1, 0, 0, 0)));

        assertThat(useCase.execute("org-1", null, null)).extracting(UnpricedModelOutput::model)
                .containsExactly("yyy", "zzz", "b", "a", "auto");
    }
}
