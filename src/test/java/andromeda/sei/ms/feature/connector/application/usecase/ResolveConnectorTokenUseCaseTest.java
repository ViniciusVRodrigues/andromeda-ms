package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenNotFoundException;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenRevokedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolveConnectorTokenUseCaseTest {

    @Mock
    private ConnectorTokenRepository connectorTokenRepository;

    @InjectMocks
    private ResolveConnectorTokenUseCase useCase;

    @Test
    void shouldReturnTokenWhenActiveAndFound() {
        ConnectorToken token = activeToken();
        when(connectorTokenRepository.findByOrganizationIdAndTokenHash("org-1", "hashvalue"))
                .thenReturn(Optional.of(token));

        ConnectorToken resolved = useCase.execute("org-1", "hashvalue");

        assertThat(resolved.getId()).isEqualTo("token-1");
    }

    @Test
    void shouldThrowNotFoundWhenTokenDoesNotExist() {
        when(connectorTokenRepository.findByOrganizationIdAndTokenHash("org-1", "12345678abcdefgh"))
                .thenReturn(Optional.empty());

        assertThrows(ConnectorTokenNotFoundException.class,
                () -> useCase.execute("org-1", "12345678abcdefgh"));
    }

    @Test
    void shouldThrowRevokedExceptionWhenTokenIsInactive() {
        ConnectorToken token = activeToken();
        token.setActive(false);
        when(connectorTokenRepository.findByOrganizationIdAndTokenHash("org-1", "hashvalue"))
                .thenReturn(Optional.of(token));

        assertThrows(ConnectorTokenRevokedException.class,
                () -> useCase.execute("org-1", "hashvalue"));
    }

    private ConnectorToken activeToken() {
        Instant now = Instant.now();
        return new ConnectorToken("token-1", "org-1", "user-1",
                "token-abcdefgh", "hashvalue", true, 0L, now, now);
    }
}
