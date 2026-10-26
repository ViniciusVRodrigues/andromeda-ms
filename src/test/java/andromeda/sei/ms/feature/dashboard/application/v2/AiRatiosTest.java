package andromeda.sei.ms.feature.dashboard.application.v2;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiRatiosTest {

    @Test
    void should_divideCostByDivisor() {
        assertThat(AiRatios.per(new BigDecimal("6"), 3.0)).isEqualByComparingTo("2");
        assertThat(AiRatios.per(new BigDecimal("6"), 12.0)).isEqualByComparingTo("0.5");
    }

    @Test
    void should_returnNull_when_divisorIsNullOrZeroOrNegative() {
        assertThat(AiRatios.per(new BigDecimal("6"), null)).isNull();
        assertThat(AiRatios.per(new BigDecimal("6"), 0.0)).isNull();
        assertThat(AiRatios.per(new BigDecimal("6"), -1.0)).isNull();
    }

    @Test
    void should_returnNull_when_costIsNull() {
        assertThat(AiRatios.per(null, 3.0)).isNull();
    }

    @Test
    void should_roundHalfUpToSixDecimals() {
        assertThat(AiRatios.per(BigDecimal.ONE, 3.0)).isEqualTo(new BigDecimal("0.333333"));
        assertThat(AiRatios.per(BigDecimal.valueOf(2), 3.0)).isEqualTo(new BigDecimal("0.666667"));
    }
}
