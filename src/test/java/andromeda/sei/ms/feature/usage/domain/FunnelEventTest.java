package andromeda.sei.ms.feature.usage.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunnelEventTest {

    private static final Instant NOW = Instant.parse("2026-10-20T13:00:00Z");
    private static final String FUNNEL = "3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b";

    private static FunnelEventDraft draft(FunnelStage stage, String step, Long ms, String value) {
        return new FunnelEventDraft(stage, NOW, step, ms, value, "lg");
    }

    private static void rejects(FunnelFlow flow, FunnelEventDraft d) {
        assertThatThrownBy(() -> FunnelEvent.fromClient(FUNNEL, flow, d, NOW))
                .isInstanceOf(FunnelEventInvalidException.class);
    }

    @Test
    void should_storeAnAnonymousEvent_withoutOrgNorUser() {
        FunnelEvent e = FunnelEvent.fromClient(FUNNEL, FunnelFlow.SIGNUP, draft(FunnelStage.STEP_ENTER, "pessoal", null, null), NOW);

        assertThat(e.getOrganizationId()).isNull();
        assertThat(e.getUserId()).isNull();
        assertThat(e.getLinkedAt()).isNull();
        assertThat(e.getStep()).isEqualTo("pessoal");
        assertThat(e.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_acceptOpenWithoutStep() {
        assertThat(FunnelEvent.fromClient(FUNNEL, FunnelFlow.INVITE, draft(FunnelStage.OPEN, null, null, null), NOW).getStep())
                .isNull();
    }

    @Test
    void should_rejectCompleteFromTheClient() {
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.COMPLETE, "confirmar", null, null));
        assertThat(FunnelRules.CLIENT_STAGE_REGEX).doesNotContain("COMPLETE");
    }

    @Test
    void should_requireAStepOutsideOpen() {
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.STEP_ENTER, null, null, null));
    }

    @Test
    void should_keepTheStepListClosedPerFlow() {
        rejects(FunnelFlow.INVITE, draft(FunnelStage.STEP_ENTER, "projeto", null, null));
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.STEP_ENTER, "bem-vindo", null, null));
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.STEP_ENTER, "inventada", null, null));
        for (String s : new String[]{"pessoal", "verificar", "senha", "org", "projeto", "senioridade", "cargos",
                "permissoes", "equipe", "confirmar"}) {
            assertThat(FunnelFlow.SIGNUP.hasStep(s)).isTrue();
        }
        for (String s : new String[]{"bem-vindo", "conta", "ia", "pronto"}) {
            assertThat(FunnelFlow.INVITE.hasStep(s)).isTrue();
        }
    }

    @Test
    void should_acceptVerifyOnlyInSignupVerificar() {
        FunnelEvent.fromClient(FUNNEL, FunnelFlow.SIGNUP, draft(FunnelStage.VERIFY_FAIL, "verificar", null, null), NOW);
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.VERIFY_RESEND, "senha", null, null));
        rejects(FunnelFlow.INVITE, draft(FunnelStage.VERIFY_RESEND, "conta", null, null));
    }

    @Test
    void should_requireVisibleMsInStepTimeWithinTheRange_andDropItElsewhere() {
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.STEP_TIME, "senha", null, null));
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.STEP_TIME, "senha", -1L, null));
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.STEP_TIME, "senha", 86_400_001L, null));
        assertThat(FunnelEvent.fromClient(FUNNEL, FunnelFlow.SIGNUP, draft(FunnelStage.STEP_TIME, "senha", 41_200L, null), NOW)
                .getVisibleMs()).isEqualTo(41_200L);
        assertThat(FunnelEvent.fromClient(FUNNEL, FunnelFlow.SIGNUP, draft(FunnelStage.STEP_ENTER, "senha", 5L, null), NOW)
                .getVisibleMs()).isNull();
    }

    @Test
    void should_acceptValueOnlyInSubmitError() {
        FunnelEvent.fromClient(FUNNEL, FunnelFlow.SIGNUP, draft(FunnelStage.SUBMIT_ERROR, "confirmar", null, "conflito-org"), NOW);
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.SUBMIT_ERROR, "confirmar", null, "joao@x.com"));
        rejects(FunnelFlow.SIGNUP, draft(FunnelStage.SUBMIT, "confirmar", null, "erro"));
    }

    @Test
    void should_rejectABadFunnelIdOrViewport() {
        assertThatThrownBy(() -> FunnelEvent.fromClient("abc", FunnelFlow.SIGNUP, draft(FunnelStage.OPEN, null, null, null), NOW))
                .isInstanceOf(FunnelEventInvalidException.class);
        rejects(FunnelFlow.SIGNUP, new FunnelEventDraft(FunnelStage.OPEN, NOW, null, null, null, "xl"));
    }

    @Test
    void should_fixTheClock_outsideTheWindow() {
        FunnelEvent old = FunnelEvent.fromClient(FUNNEL, FunnelFlow.SIGNUP,
                new FunnelEventDraft(FunnelStage.OPEN, NOW.minusSeconds(90_000), null, null, null, null), NOW);
        FunnelEvent future = FunnelEvent.fromClient(FUNNEL, FunnelFlow.SIGNUP,
                new FunnelEventDraft(FunnelStage.OPEN, NOW.plusSeconds(600), null, null, null, null), NOW);
        FunnelEvent ok = FunnelEvent.fromClient(FUNNEL, FunnelFlow.SIGNUP,
                new FunnelEventDraft(FunnelStage.OPEN, NOW.minusSeconds(60), null, null, null, null), NOW);

        assertThat(old.getOccurredAt()).isEqualTo(NOW);
        assertThat(future.getOccurredAt()).isEqualTo(NOW);
        assertThat(ok.getOccurredAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void should_buildACompleteTiedToTheAccount() {
        FunnelEvent c = FunnelEvent.complete(FUNNEL, FunnelFlow.INVITE, "org-1", "user-1", NOW);

        assertThat(c.getStage()).isEqualTo(FunnelStage.COMPLETE);
        assertThat(c.getOrganizationId()).isEqualTo("org-1");
        assertThat(c.getUserId()).isEqualTo("user-1");
        assertThat(c.getFlow()).isEqualTo(FunnelFlow.INVITE);
    }
}
