package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.application.port.FluencyScoreRepository;
import andromeda.sei.ms.feature.connector.domain.FluencyScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFluencyScoreUseCaseTest {

    @Mock
    private FluencyScoreRepository fluencyScoreRepository;

    @InjectMocks
    private GetFluencyScoreUseCase useCase;

    @Test
    void shouldReturnScoreWhenPresent() {
        FluencyScore score = new FluencyScore("score-1", "org-1", "user-1",
                3, "Praticante", "2026-08-14T00:00:00Z", List.of(),
                true, 0L, Instant.now(), Instant.now());
        when(fluencyScoreRepository.findByOrganizationIdAndUserId("org-1", "user-1"))
                .thenReturn(Optional.of(score));

        assertThat(useCase.execute("org-1", "user-1")).contains(score);
    }

    /**
     * Não ter score é o estado inicial de todo usuário novo, não uma condição
     * excepcional. Lançar aqui fazia o endpoint responder 404 e a UI exibir "erro ao
     * carregar" em vez do empty state — este teste trava o contrato de empty-not-error.
     */
    @Test
    void shouldReturnEmptyInsteadOfThrowingWhenUserHasNoScoreYet() {
        when(fluencyScoreRepository.findByOrganizationIdAndUserId("org-1", "user-sem-score"))
                .thenReturn(Optional.empty());

        assertThat(useCase.execute("org-1", "user-sem-score")).isEmpty();
    }
}
