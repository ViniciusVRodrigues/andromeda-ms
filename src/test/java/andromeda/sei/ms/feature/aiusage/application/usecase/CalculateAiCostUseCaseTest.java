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

        // output only: 2000 * 0.015 / 1000 = 0.030 (the input price/tokens are ignored)
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.030000"));
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
        AIUsage usage = usage("gpt-4o", 0L, 1L);
        List<AiPriceEntry> prices = List.of(
                new AiPriceEntry("gpt-4o", new BigDecimal("0.003"), new BigDecimal("0.015"))
        );

        BigDecimal cost = useCase.execute(usage, prices);

        // 1 * 0.015 / 1000 = 0.000015
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.000015"));
    }

    private static List<AiPriceEntry> one(String model) {
        return List.of(new AiPriceEntry(model, new BigDecimal("1"), new BigDecimal("10")));
    }

    @Test
    void should_ignoreInput_when_inputTokensAreHuge() {
        BigDecimal cost = useCase.execute(usage("gpt-4o", 9_000_000L, 1000L), one("gpt-4o"));

        assertThat(cost).isEqualByComparingTo(new BigDecimal("10.000000"));
    }

    @Test
    void should_priceFree_when_outputPriceIsZero() {
        List<AiPriceEntry> prices = List.of(new AiPriceEntry("free", null, BigDecimal.ZERO));

        assertThat(useCase.execute(usage("free", 5L, 5L), prices)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_returnNull_when_rowHasNoOutputPrice() {
        List<AiPriceEntry> prices = List.of(new AiPriceEntry("gpt-4o", BigDecimal.ONE, null));
        assertThat(useCase.execute(usage("gpt-4o", 1000, 1000), prices)).isNull();
        assertThat(useCase.findPrice("gpt-4o", prices)).isEmpty();
    }

    @Test
    void should_matchModel_when_usageHasDateSuffix() {
        assertThat(useCase.findPrice("claude-sonnet-4-5-20250929", one("claude-sonnet-4-5"))).isPresent();
        assertThat(useCase.findPrice("gpt-4o-2024-08-06", one("gpt-4o"))).isPresent();
        assertThat(useCase.findPrice("claude-3-opus@20240229", one("claude-3-opus"))).isPresent();
    }

    @Test
    void should_matchModel_when_tablehasDateSuffixAndUsageDoesNot() {
        assertThat(useCase.findPrice("gpt-4o", one("gpt-4o-2024-08-06"))).isPresent();
    }

    @Test
    void should_matchModel_when_dotVersusDashCaseAndSpaces() {
        assertThat(useCase.findPrice("Claude-Opus-4.7 ", one("claude-opus-4-7"))).isPresent();
        assertThat(useCase.findPrice("claude-opus-4-7", one("claude-opus-4.7"))).isPresent();
    }

    @Test
    void should_notMatch_when_differentModelOrPartialSuffix() {
        assertThat(useCase.findPrice("gpt-4o-mini", one("gpt-4o"))).isEmpty();
        assertThat(useCase.findPrice("gpt-4o-123", one("gpt-4o"))).isEmpty();
    }

    @Test
    void should_preferExactMatch_overNormalized() {
        List<AiPriceEntry> prices = List.of(
                new AiPriceEntry("gpt-4o", BigDecimal.ONE, new BigDecimal("1")),
                new AiPriceEntry("gpt-4o-2024-08-06", BigDecimal.ONE, new BigDecimal("2")));

        assertThat(useCase.findPrice("gpt-4o-2024-08-06", prices).orElseThrow().getPricePerKOutput())
                .isEqualByComparingTo("2");
    }

    @Test
    void should_neverPrice_autoUnknownBlankOrNull_evenWithARowByThatName() {
        for (String m : new String[] {"auto", "AUTO", "unknown", "Unknown", "", "  ", null}) {
            assertThat(useCase.isBillable(m)).as("billable %s", m).isFalse();
            assertThat(useCase.findPrice(m, one(m == null ? "x" : m))).as("price %s", m).isEmpty();
        }
        assertThat(useCase.isBillable("gpt-4o")).isTrue();
        assertThat(useCase.execute(usage("auto", 1L, 1000L), one("auto"))).isNull();
    }

    private AIUsage agent(long in, long out, long cacheRead, long cacheWrite, long thinking) {
        AIUsage u = usage("claude-opus-4-8", in, out);
        u.setSource("AGENT");
        u.setCacheReadTokens(cacheRead);
        u.setCacheWriteTokens(cacheWrite);
        u.setThinkingTokens(thinking);
        return u;
    }

    private List<AiPriceEntry> opus(String cacheRead, String cacheWrite) {
        return List.of(new AiPriceEntry("claude-opus-4-8", new BigDecimal("0.005"), new BigDecimal("0.025"),
                cacheRead == null ? null : new BigDecimal(cacheRead),
                cacheWrite == null ? null : new BigDecimal(cacheWrite)));
    }

    @Test
    void should_priceInputCacheAndOutput_whenTheRowIsFromTheAgent() {
        BigDecimal cost = useCase.execute(agent(1000, 2000, 1_000_000, 10_000, 0), opus("0.0005", "0.00625"));

        // 1000*0.005 + 1000000*0.0005 + 10000*0.00625 + 2000*0.025 = 5 + 500 + 62.5 + 50 = 617.5 -> /1000
        assertThat(cost).isEqualByComparingTo("0.617500");
    }

    @Test
    void should_treatMissingCachePricesAsZero_whenTheRowIsFromTheAgent() {
        BigDecimal cost = useCase.execute(agent(1000, 2000, 1_000_000, 10_000, 0), opus(null, null));

        // 1000*0.005 + 2000*0.025 = 55 -> /1000
        assertThat(cost).isEqualByComparingTo("0.055000");
    }

    @Test
    void should_neverSumThinking_whenTheRowIsFromTheAgent() {
        BigDecimal with = useCase.execute(agent(1000, 2000, 0, 0, 5000), opus(null, null));
        BigDecimal without = useCase.execute(agent(1000, 2000, 0, 0, 0), opus(null, null));

        assertThat(with).isEqualByComparingTo(without);
    }

    @Test
    void should_keepOutputOnly_whenTheRowIsFromTheAiefEvenWithCachePrices() {
        AIUsage u = usage("claude-opus-4-8", 613000, 2000);
        u.setCacheReadTokens(600000);

        assertThat(useCase.execute(u, opus("0.0005", "0.00625"))).isEqualByComparingTo("0.050000");
    }

    @Test
    void should_returnNull_whenTheAgentRowHasNoPrice() {
        assertThat(useCase.execute(agent(1000, 2000, 0, 0, 0), List.of())).isNull();
    }
}
