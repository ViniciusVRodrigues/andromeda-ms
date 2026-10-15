package andromeda.sei.ms.feature.usage.domain;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

/** Format and coherence rules of the anonymous funnel contract. */
public final class FunnelRules {

    public static final String UUID_V4_REGEX = UsageEventRules.UUID_V4_REGEX;
    public static final String FLOW_REGEX = "^(signup|invite)$";
    /** Every stage a client may send: {@code COMPLETE} is deliberately absent. */
    public static final String CLIENT_STAGE_REGEX =
            "^(OPEN|ENGAGE|STEP_ENTER|STEP_TIME|VERIFY_RESEND|VERIFY_FAIL|SUBMIT|SUBMIT_ERROR|CANCEL)$";
    public static final String STEP_REGEX = "^[a-z]+(-[a-z]+)*$";
    public static final String VALUE_REGEX = "^(conflito-email|conflito-org|erro)$";
    public static final String VERIFY_STEP = "verificar";

    public static final int MAX_BATCH = 20;
    public static final long MAX_VISIBLE_MS = UsageEventRules.MAX_VISIBLE_MS;
    /** A funnel id can only be linked to an account while its events are this recent. */
    public static final Duration LINK_WINDOW = Duration.ofDays(7);

    private static final Pattern UUID_V4 = Pattern.compile(UUID_V4_REGEX);
    private static final Set<String> VALUES = Set.of("conflito-email", "conflito-org", "erro");

    private FunnelRules() {
    }

    public static boolean isFunnelId(String candidate) {
        return candidate != null && UUID_V4.matcher(candidate).matches();
    }

    /** The first rule the draft breaks, or {@code null} when it is coherent with the flow. */
    public static String violation(FunnelFlow flow, FunnelEventDraft d) {
        if (flow == null) return "fluxo ausente";
        if (d == null || d.stage() == null) return "etapa ausente";
        if (d.stage() == FunnelStage.COMPLETE) return "COMPLETE é gravado só pelo servidor";
        if (d.stage() == FunnelStage.OPEN) {
            if (d.step() != null && !flow.hasStep(d.step())) return "step fora da lista do fluxo";
        } else if (!flow.hasStep(d.step())) {
            return "step ausente ou fora da lista do fluxo";
        }
        if (d.stage().isVerify() && (flow != FunnelFlow.SIGNUP || !VERIFY_STEP.equals(d.step()))) {
            return "VERIFY_* só vale em signup/verificar";
        }
        if (d.stage() == FunnelStage.STEP_TIME) {
            if (d.visibleMs() == null) return "STEP_TIME sem visibleMs";
            if (d.visibleMs() < 0 || d.visibleMs() > MAX_VISIBLE_MS) return "visibleMs fora do intervalo";
        }
        if (d.value() != null && (d.stage() != FunnelStage.SUBMIT_ERROR || !VALUES.contains(d.value()))) {
            return "value só vale em SUBMIT_ERROR";
        }
        if (d.viewport() != null && !UsageEventRules.VIEWPORTS.contains(d.viewport())) return "viewport inválido";
        return null;
    }
}
