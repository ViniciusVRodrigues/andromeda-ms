package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.domain.exception.WorkItemPatternInvalidException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewWorkItemPatternsUseCaseTest {

    private final PreviewWorkItemPatternsUseCase useCase = new PreviewWorkItemPatternsUseCase();

    @Test
    void should_previewDefaults_when_noPatterns() {
        var r = useCase.execute(null, List.of("feature/AB#12-x", "main"));

        assertThat(r.get(0).workItemNumber()).isEqualTo("12");
        assertThat(r.get(0).patternIndex()).isZero();
        assertThat(r.get(0).matchStart()).isEqualTo(8);
        assertThat(r.get(0).matchEnd()).isEqualTo(13);
        assertThat(r.get(1).workItemNumber()).isNull();
        assertThat(r.get(1).patternIndex()).isNull();
    }

    @Test
    void should_useGivenPatterns_andReportTheirIndex() {
        var r = useCase.execute(List.of("zzz(\\d+)", "^wi-(\\d+)"), List.of("wi-77"));
        assertThat(r.get(0).workItemNumber()).isEqualTo("77");
        assertThat(r.get(0).patternIndex()).isEqualTo(1);
    }

    @Test
    void should_notMatchAndReturnFast_when_regexIsCatastrophic() {
        var r = useCase.execute(List.of("(a+)+$"), List.of("a".repeat(40) + "!"));
        assertThat(r.get(0).workItemNumber()).isNull();
    }

    @Test
    void should_reject_when_patternInvalid() {
        assertThatThrownBy(() -> useCase.execute(List.of("no-group"), List.of("x")))
                .isInstanceOf(WorkItemPatternInvalidException.class);
    }
}
