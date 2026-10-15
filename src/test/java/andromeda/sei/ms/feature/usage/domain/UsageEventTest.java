package andromeda.sei.ms.feature.usage.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsageEventTest {

    private static final Instant NOW = Instant.parse("2026-10-20T13:00:00Z");
    private static final String SESSION = "3f2b1c4e-5a6d-4e7f-8a9b-0c1d2e3f4a5b";
    private static final String VIEW = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

    private static UsageEventDraft.Builder base(UsageEventType type) {
        return UsageEventDraft.builder(type, NOW).sessionId(SESSION).viewId(VIEW).screen("dashboard/equipe/resumo");
    }

    private static UsageEvent create(UsageEventDraft draft) {
        return UsageEvent.fromClient("org-1", "user-1", draft, NOW);
    }

    @Test
    void should_acceptValidPageView_withIdentityFromTheCaller() {
        UsageEvent event = create(base(UsageEventType.PAGE_VIEW).viewport("lg").build());

        assertThat(event.getOrganizationId()).isEqualTo("org-1");
        assertThat(event.getUserId()).isEqualTo("user-1");
        assertThat(event.getType()).isEqualTo(UsageEventType.PAGE_VIEW);
        assertThat(event.getScreen()).isEqualTo("dashboard/equipe/resumo");
        assertThat(event.getViewport()).isEqualTo("lg");
        assertThat(event.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_acceptSingleSegmentAndHyphenatedScreens() {
        assertThat(create(base(UsageEventType.PAGE_VIEW).screen("backlog").build()).getScreen()).isEqualTo("backlog");
        assertThat(create(base(UsageEventType.PAGE_VIEW).screen("uso-ia/precificacao").build()).getScreen())
                .isEqualTo("uso-ia/precificacao");
    }

    @Test
    void should_rejectScreensWithIdentifiersUppercaseOrTooLong() {
        for (String screen : new String[]{"dashboard/123", "dashboard/64f1a2b3c4d5e6f7a8b9c0d1", "Dashboard",
                "a/b/c/d/e", "-x", "x-", "a//b", "a b", "a".repeat(81), ""}) {
            assertThatThrownBy(() -> create(base(UsageEventType.PAGE_VIEW).screen(screen).build()))
                    .as(screen)
                    .isInstanceOf(UsageEventInvalidException.class);
        }
    }

    @Test
    void should_rejectSessionOrViewThatAreNotUuidV4() {
        assertThatThrownBy(() -> create(base(UsageEventType.PAGE_VIEW).sessionId("abc").build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.PAGE_VIEW).viewId("3f2b1c4e-5a6d-1e7f-8a9b-0c1d2e3f4a5b").build()))
                .isInstanceOf(UsageEventInvalidException.class);
    }

    @Test
    void should_rejectUnknownViewport() {
        assertThatThrownBy(() -> create(base(UsageEventType.PAGE_VIEW).viewport("xl").build()))
                .isInstanceOf(UsageEventInvalidException.class);
    }

    @Test
    void should_rejectLoginFromTheClient() {
        assertThatThrownBy(() -> create(base(UsageEventType.LOGIN).build()))
                .isInstanceOf(UsageEventInvalidException.class);
    }

    @Test
    void should_requireVisibleMs_onScreenTime_within0And86400000() {
        assertThatThrownBy(() -> create(base(UsageEventType.SCREEN_TIME).build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.SCREEN_TIME).visibleMs(-1L).build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.SCREEN_TIME).visibleMs(86_400_001L).build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThat(create(base(UsageEventType.SCREEN_TIME).visibleMs(86_400_000L).build()).getVisibleMs()).isEqualTo(86_400_000L);
    }

    @Test
    void should_requireElementAndArea_onClick_andSanitizeTheLabel() {
        assertThatThrownBy(() -> create(base(UsageEventType.CLICK).area("main").build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.CLICK).element("button").build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.CLICK).element("div").area("main").build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.CLICK).element("button").area("Main").build()))
                .isInstanceOf(UsageEventInvalidException.class);

        UsageEvent click = create(base(UsageEventType.CLICK).element("button").area("main")
                .label("Sprint 42 de ana@acme.com").build());
        assertThat(click.getLabel()).isEqualTo("Sprint ## de [email]");
    }

    @Test
    void should_requireActionFormat_onAction_andValueFormat() {
        assertThatThrownBy(() -> create(base(UsageEventType.ACTION).build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.ACTION).action("period_change").build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.ACTION).action("PERIOD_CHANGE").value("A B").build()))
                .isInstanceOf(UsageEventInvalidException.class);

        UsageEvent action = create(base(UsageEventType.ACTION).action("PERIOD_CHANGE").value("90").build());
        assertThat(action.getAction()).isEqualTo("PERIOD_CHANGE");
        assertThat(action.getValue()).isEqualTo("90");
    }

    @Test
    void should_acceptApiError_withStatusAndCode() {
        UsageEvent event = create(base(UsageEventType.ERROR).origin("api").status(500).code("AZURE_SYNC_FAILED").build());

        assertThat(event.getOrigin()).isEqualTo("api");
        assertThat(event.getStatus()).isEqualTo(500);
        assertThat(event.getCode()).isEqualTo("AZURE_SYNC_FAILED");
    }

    @Test
    void should_acceptNetworkFailureAsStatusZero() {
        assertThat(create(base(UsageEventType.ERROR).origin("api").status(0).code("NETWORK_ERROR").build()).getStatus()).isZero();
    }

    @Test
    void should_acceptRuntimeErrorWithoutStatus() {
        UsageEvent event = create(base(UsageEventType.ERROR).origin("runtime").code("UNCAUGHT_ERROR").build());
        assertThat(event.getStatus()).isNull();
    }

    @Test
    void should_rejectErrorWithoutOrigin_orWithUnknownOrigin() {
        assertThatThrownBy(() -> create(base(UsageEventType.ERROR).status(500).code("X_ERR").build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.ERROR).origin("server").status(500).code("X_ERR").build()))
                .isInstanceOf(UsageEventInvalidException.class);
    }

    @Test
    void should_rejectApiErrorWithoutStatus_orOutOfRange() {
        assertThatThrownBy(() -> create(base(UsageEventType.ERROR).origin("api").code("X_ERR").build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.ERROR).origin("api").status(600).code("X_ERR").build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.ERROR).origin("api").status(-1).code("X_ERR").build()))
                .isInstanceOf(UsageEventInvalidException.class);
    }

    @Test
    void should_rejectStatusOutsideApiOrigin() {
        assertThatThrownBy(() -> create(base(UsageEventType.ERROR).origin("runtime").status(500).code("UNCAUGHT_ERROR").build()))
                .isInstanceOf(UsageEventInvalidException.class);
        assertThatThrownBy(() -> create(base(UsageEventType.ERROR).origin("render").status(0).code("RENDER_ERROR").build()))
                .isInstanceOf(UsageEventInvalidException.class);
    }

    @Test
    void should_rejectStatus401_becauseExpiredSessionIsNotAnError() {
        assertThatThrownBy(() -> create(base(UsageEventType.ERROR).origin("api").status(401).code("UNAUTHORIZED").build()))
                .isInstanceOf(UsageEventInvalidException.class);
    }

    @Test
    void should_rejectErrorCodeThatIsLowercaseMissingOrMalformed() {
        for (String code : new String[]{"azure_sync", null, "A", "1ABC", "A".repeat(65), "ABC DEF"}) {
            assertThatThrownBy(() -> create(base(UsageEventType.ERROR).origin("api").status(500).code(code).build()))
                    .as(String.valueOf(code))
                    .isInstanceOf(UsageEventInvalidException.class);
        }
    }

    @Test
    void should_dropFieldsThatDoNotBelongToTheType() {
        UsageEvent event = create(base(UsageEventType.PAGE_VIEW)
                .label("segredo").element("button").area("main").action("ABC").value("x").code("ABC_D").origin("api").status(500)
                .visibleMs(10L).build());

        assertThat(event.getLabel()).isNull();
        assertThat(event.getElement()).isNull();
        assertThat(event.getArea()).isNull();
        assertThat(event.getAction()).isNull();
        assertThat(event.getValue()).isNull();
        assertThat(event.getCode()).isNull();
        assertThat(event.getOrigin()).isNull();
        assertThat(event.getStatus()).isNull();
        assertThat(event.getVisibleMs()).isNull();
    }

    @Test
    void should_keepOccurredAt_insideTheWindow() {
        Instant at = NOW.minus(Duration.ofHours(23));
        assertThat(create(UsageEventDraft.builder(UsageEventType.PAGE_VIEW, at).sessionId(SESSION).viewId(VIEW)
                .screen("backlog").build()).getOccurredAt()).isEqualTo(at);
        Instant slightlyAhead = NOW.plus(Duration.ofMinutes(4));
        assertThat(create(UsageEventDraft.builder(UsageEventType.PAGE_VIEW, slightlyAhead).sessionId(SESSION).viewId(VIEW)
                .screen("backlog").build()).getOccurredAt()).isEqualTo(slightlyAhead);
    }

    @Test
    void should_replaceOccurredAtByReceiptTime_whenOutsideTheWindow() {
        for (Instant at : new Instant[]{NOW.minus(Duration.ofDays(3)), NOW.minus(Duration.ofHours(24)).minusSeconds(1),
                NOW.plus(Duration.ofMinutes(5)).plusSeconds(1), null}) {
            UsageEventDraft draft = UsageEventDraft.builder(UsageEventType.PAGE_VIEW, at).sessionId(SESSION).viewId(VIEW)
                    .screen("backlog").build();
            assertThat(create(draft).getOccurredAt()).as(String.valueOf(at)).isEqualTo(NOW);
        }
    }

    @Test
    void should_buildServerSideLogin() {
        UsageEvent login = UsageEvent.login("org-1", "user-1", LoginMethod.SIGNUP, NOW);

        assertThat(login.getType()).isEqualTo(UsageEventType.LOGIN);
        assertThat(login.getValue()).isEqualTo("signup");
        assertThat(login.getOrganizationId()).isEqualTo("org-1");
        assertThat(login.getUserId()).isEqualTo("user-1");
        assertThat(login.getOccurredAt()).isEqualTo(NOW);
        assertThat(login.getScreen()).isNull();
        assertThat(LoginMethod.PASSWORD.value()).isEqualTo("password");
        assertThat(LoginMethod.INVITE.value()).isEqualTo("invite");
    }
}
