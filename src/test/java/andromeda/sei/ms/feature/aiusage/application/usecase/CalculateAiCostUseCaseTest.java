package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalculateAiCostUseCaseTest {

    private CalculateAiCostUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CalculateAiCostUseCase();
    }

    private AIUsage usage(String model, long inputTokens, long outputTokens) {
        return new AIUsage(null, "org-1", "user-1", null, "ds-1",
                LocalDate.now(), model, "ws-1", null, "m-1", null,
                inputTokens, outputTokens, 1, null, null, null,
                Instant.now(), true, null, Instant.now(), Instant.now());
    }

    @Test
    void should_computeCost_when_modelMatches() {
        AIUsage usage = usage("gpt-4o", 1000L, 2000L);
        List<AiPriceEntry> prices = List.of(
                new AiPriceEntry("gpt-4o", new BigDecimal("0.005"), new BigDecimal("0.015"))
        );

        BigDecimal cost = useCase.execute(usage, prices);

        // (1000 * 0.005 / 1000) + (2000 * 0.015 / 1000) = 0.005 + 0.030 = 0.035
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.035000"));
    }

    @Test
    void should_returnNull_when_modelNotFound() {
        AIUsage usage = usage("gpt-4o", 1000L, 1000L);
        List<AiPriceEntry> prices = List.of(
                new AiPriceEntry("claude-3-5-sonnet", new BigDecimal("0.003"), new BigDecimal("0.015"))
        );

        BigDecimal cost = useCase.execute(usage, prices);

        assertThat(cost).isNull();
    }

    @Test
    void should_returnNull_when_priceListIsEmpty() {
        AIUsage usage = usage("gpt-4o", 1000L, 1000L);

        BigDecimal cost = useCase.execute(usage, List.of());

        assertThat(cost).isNull();
    }

    @Test
    void should_returnNull_when_priceListIsNull() {
        AIUsage usage = usage("gpt-4o", 1000L, 1000L);

        BigDecimal cost = useCase.execute(usage, null);

        assertThat(cost).isNull();
    }

    @Test
    void should_returnZero_when_tokensAreZero() {
        AIUsage usage = usage("gpt-4o", 0L, 0L);
        List<AiPriceEntry> prices = List.of(
                new AiPriceEntry("gpt-4o", new BigDecimal("0.005"), new BigDecimal("0.015"))
        );

        BigDecimal cost = useCase.execute(usage, prices);

        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_maintainPrecision_when_singleTokenWithSmallPrice() {
        AIUsage usage = usage("gpt-4o", 1L, 0L);
        List<AiPriceEntry> prices = List.of(
                new AiPriceEntry("gpt-4o", new BigDecimal("0.003"), new BigDecimal("0.015"))
        );

        BigDecimal cost = useCase.execute(usage, prices);

        // 1 * 0.003 / 1000 = 0.000003
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.000003"));
    }
}
