package andromeda.sei.ms.feature.dashboardlayout.domain;

import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutInvalidException;
import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutViewUnknownException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardLayoutTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";

    @Test
    void should_parseEveryAllowedViewKey() {
        assertThat(DashboardViewKey.parse("individual")).isEqualTo(DashboardViewKey.INDIVIDUAL);
        assertThat(DashboardViewKey.parse("equipe")).isEqualTo(DashboardViewKey.EQUIPE);
        assertThat(DashboardViewKey.parse("projeto")).isEqualTo(DashboardViewKey.PROJETO);
        assertThat(DashboardViewKey.parse("org")).isEqualTo(DashboardViewKey.ORG);
    }

    @Test
    void should_rejectUnknownViewKey_withViewUnknownCode() {
        for (String value : new String[]{"qualquer", "EQUIPE", "", null}) {
            assertThatThrownBy(() -> DashboardViewKey.parse(value))
                    .isInstanceOf(DashboardLayoutViewUnknownException.class)
                    .extracting("code").isEqualTo("DASHBOARD_LAYOUT_VIEW_UNKNOWN");
        }
    }

    @Test
    void should_parseEverySize_andRejectOthers() {
        assertThat(WidgetSize.parse("P")).isEqualTo(WidgetSize.P);
        assertThat(WidgetSize.parse("I")).isEqualTo(WidgetSize.I);
        assertThatThrownBy(() -> WidgetSize.parse("XL")).isInstanceOf(DashboardLayoutInvalidException.class);
        assertThatThrownBy(() -> WidgetSize.parse(null)).isInstanceOf(DashboardLayoutInvalidException.class);
    }

    @Test
    void should_createActiveLayout_withoutVersion_whenContentIsValid() {
        DashboardLayout layout = DashboardLayout.create(ORG, USER, DashboardViewKey.EQUIPE,
                List.of(section("fluxo", widget("equipe.burndown", WidgetSize.G))), List.of("equipe.commits-dia"));

        assertThat(layout.isActive()).isTrue();
        assertThat(layout.getVersion()).isNull();
        assertThat(layout.getOrganizationId()).isEqualTo(ORG);
        assertThat(layout.getUserId()).isEqualTo(USER);
        assertThat(layout.getHidden()).containsExactly("equipe.commits-dia");
    }

    @Test
    void should_acceptEmptyLayout() {
        assertThatCode(() -> DashboardLayout.create(ORG, USER, DashboardViewKey.ORG, List.of(), List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void should_rejectWidgetIdOutOfFormat() {
        for (String id : new String[]{"burndown", "Equipe.burndown", "equipe.burn_down", "equipe..x", "a.b.c", ""}) {
            assertInvalid(List.of(section("fluxo", widget(id, WidgetSize.M))), List.of());
        }
    }

    @Test
    void should_rejectHiddenIdOutOfFormat() {
        assertInvalid(List.of(), List.of("sem-ponto"));
    }

    @Test
    void should_rejectSectionIdOutOfFormat() {
        assertInvalid(List.of(section("flu.xo")), List.of());
        assertInvalid(List.of(section("Fluxo")), List.of());
    }

    @Test
    void should_rejectDuplicateWidget_acrossSections() {
        assertInvalid(List.of(section("fluxo", widget("equipe.a", WidgetSize.P)),
                section("codigo", widget("equipe.a", WidgetSize.M))), List.of());
    }

    @Test
    void should_rejectWidgetThatIsAlsoHidden() {
        assertInvalid(List.of(section("fluxo", widget("equipe.a", WidgetSize.P))), List.of("equipe.a"));
    }

    @Test
    void should_rejectDuplicateHidden() {
        assertInvalid(List.of(), List.of("equipe.a", "equipe.a"));
    }

    @Test
    void should_rejectDuplicateSectionIds() {
        assertInvalid(List.of(section("fluxo"), section("fluxo")), List.of());
    }

    @Test
    void should_rejectWidgetWithoutSize() {
        assertInvalid(List.of(section("fluxo", widget("equipe.a", null))), List.of());
    }

    @Test
    void should_rejectNullCollections() {
        assertInvalid(null, List.of());
        assertInvalid(List.of(new DashboardSection("fluxo", false, null)), List.of());
        assertInvalid(List.of(), null);
    }

    @Test
    void should_acceptSixtyItems_andRejectSixtyOne() {
        assertThatCode(() -> DashboardLayout.create(ORG, USER, DashboardViewKey.EQUIPE,
                List.of(section("fluxo", widgets(40))), hidden(20))).doesNotThrowAnyException();

        assertInvalid(List.of(section("fluxo", widgets(61))), List.of());
        assertInvalid(List.of(section("fluxo", widgets(41))), hidden(20));
    }

    @Test
    void should_replaceContent_keepingIdentityAndVersion() {
        DashboardLayout layout = new DashboardLayout("l-1", ORG, USER, DashboardViewKey.EQUIPE,
                List.of(), List.of(), true, 3L, null, null);

        layout.replaceContent(List.of(section("fluxo", widget("equipe.a", WidgetSize.I))), List.of());

        assertThat(layout.getId()).isEqualTo("l-1");
        assertThat(layout.getVersion()).isEqualTo(3L);
        assertThat(layout.getSections()).hasSize(1);
    }

    @Test
    void should_validateOnReplace() {
        DashboardLayout layout = new DashboardLayout("l-1", ORG, USER, DashboardViewKey.EQUIPE,
                List.of(), List.of(), true, 3L, null, null);

        assertThatThrownBy(() -> layout.replaceContent(List.of(), List.of("bad")))
                .isInstanceOf(DashboardLayoutInvalidException.class);
        assertThat(layout.getHidden()).isEmpty();
    }

    @Test
    void should_deactivate_onRestoreDefault() {
        DashboardLayout layout = DashboardLayout.create(ORG, USER, DashboardViewKey.EQUIPE, List.of(), List.of());

        layout.deactivate();

        assertThat(layout.isActive()).isFalse();
    }

    @Test
    void should_copyCollections_soCallerMutationsDoNotLeak() {
        List<String> hidden = new ArrayList<>(List.of("equipe.a"));
        DashboardLayout layout = DashboardLayout.create(ORG, USER, DashboardViewKey.EQUIPE, List.of(), hidden);

        hidden.add("equipe.b");

        assertThat(layout.getHidden()).containsExactly("equipe.a");
    }

    private static void assertInvalid(List<DashboardSection> sections, List<String> hidden) {
        assertThatThrownBy(() -> DashboardLayout.create(ORG, USER, DashboardViewKey.EQUIPE, sections, hidden))
                .isInstanceOf(DashboardLayoutInvalidException.class)
                .extracting("code").isEqualTo("DASHBOARD_LAYOUT_INVALID");
    }

    private static DashboardSection section(String id, DashboardWidget... widgets) {
        return new DashboardSection(id, false, List.of(widgets));
    }

    private static DashboardSection section(String id, List<DashboardWidget> widgets) {
        return new DashboardSection(id, false, widgets);
    }

    private static DashboardWidget widget(String id, WidgetSize size) {
        return new DashboardWidget(id, size);
    }

    private static List<DashboardWidget> widgets(int count) {
        return IntStream.range(0, count).mapToObj(i -> widget("equipe.w" + i, WidgetSize.P)).toList();
    }

    private static List<String> hidden(int count) {
        return IntStream.range(0, count).mapToObj(i -> "equipe.h" + i).toList();
    }
}
