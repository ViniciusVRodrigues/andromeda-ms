package andromeda.sei.ms.feature.vcs.infrastructure.event;

import andromeda.sei.ms.feature.vcs.application.usecase.ReattributeVcsAuthorshipUseCase;
import andromeda.sei.ms.shared.application.security.VcsIdentityLinked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VcsIdentityLinkedListenerTest {

    @Mock private ReattributeVcsAuthorshipUseCase useCase;

    @Test
    void should_runTheAttribution() {
        new VcsIdentityLinkedListener(useCase).on(new VcsIdentityLinked("org-1", "u1"));

        verify(useCase).execute("org-1", "u1");
    }

    @Test
    void should_swallowFailures_soTheLinkNeverFails() {
        doThrow(new IllegalStateException("boom")).when(useCase).execute("org-1", "u1");

        assertThatCode(() -> new VcsIdentityLinkedListener(useCase).on(new VcsIdentityLinked("org-1", "u1")))
                .doesNotThrowAnyException();
    }
}
