package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Health;

import java.util.ArrayList;
import java.util.List;

/**
 * The health of a project in the organization portfolio, as the preview's {@code healthOf}: one
 * reason per alert — the latest past sprint delivered under {@value #MIN_PREDICTABILITY_PCT}% of the
 * plan, {@value #MAX_STALE_PRS}+ stalled pull requests, change failure rate above
 * {@value #MAX_CFR_PCT}%, the current sprint more than {@value #MAX_PROGRESS_GAP_PCT} p.p. behind
 * the elapsed time — {@code ok} without reasons, {@code attention} with one, {@code risk} with two
 * or more. A missing figure ({@code null}) never raises an alert.
 */
public final class PortfolioHealth {

    static final double MIN_PREDICTABILITY_PCT = 70;
    static final int MAX_STALE_PRS = 3;
    static final double MAX_CFR_PCT = 15;
    static final double MAX_PROGRESS_GAP_PCT = 25;
    static final String OK = "ok";
    static final String ATTENTION = "attention";
    static final String RISK = "risk";
    private static final int RISK_REASONS = 2;

    private PortfolioHealth() {}

    public static Health of(Double lastPredictabilityPct, Integer stalePrs, Double cfrPct, Double progressGapPct) {
        List<String> reasons = new ArrayList<>();
        if (lastPredictabilityPct != null && lastPredictabilityPct < MIN_PREDICTABILITY_PCT) {
            reasons.add("entregou " + Math.round(lastPredictabilityPct) + "% do planejado na última sprint");
        }
        if (stalePrs != null && stalePrs >= MAX_STALE_PRS) {
            reasons.add(stalePrs + " PRs parados sem review");
        }
        if (cfrPct != null && cfrPct > MAX_CFR_PCT) {
            reasons.add("taxa de falha de " + Math.round(cfrPct) + "%");
        }
        if (progressGapPct != null && progressGapPct > MAX_PROGRESS_GAP_PCT) {
            reasons.add("sprint atual " + Math.round(progressGapPct) + " p.p. atrás da linha ideal");
        }
        String level = reasons.size() >= RISK_REASONS ? RISK : reasons.isEmpty() ? OK : ATTENTION;
        return new Health(level, List.copyOf(reasons));
    }
}
