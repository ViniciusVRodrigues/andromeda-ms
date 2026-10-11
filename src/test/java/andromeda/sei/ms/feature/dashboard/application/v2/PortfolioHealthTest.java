package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Health;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** dashboards-customizaveis 1.11 — the preview's {@code healthOf}, with nulls never alerting. */
class PortfolioHealthTest {

    @Test
    void should_beOk_when_noFigureCrossesItsThreshold() {
        assertThat(PortfolioHealth.of(70.0, 2, 15.0, 25.0)).isEqualTo(new Health("ok", java.util.List.of()));
    }

    @Test
    void should_beOk_when_everyFigureIsMissing() {
        assertThat(PortfolioHealth.of(null, null, null, null).level()).isEqualTo("ok");
    }

    @Test
    void should_needAttention_withOneReason() {
        Health health = PortfolioHealth.of(65.4, 0, null, null);

        assertThat(health.level()).isEqualTo("attention");
        assertThat(health.reasons()).containsExactly("entregou 65% do planejado na última sprint");
    }

    @Test
    void should_beAtRisk_withTwoOrMoreReasons_inThePreviewOrder() {
        Health health = PortfolioHealth.of(50.0, 3, 15.6, 30.2);

        assertThat(health.level()).isEqualTo("risk");
        assertThat(health.reasons()).containsExactly("entregou 50% do planejado na última sprint",
                "3 PRs parados sem review", "taxa de falha de 16%", "sprint atual 30 p.p. atrás da linha ideal");
    }
}
