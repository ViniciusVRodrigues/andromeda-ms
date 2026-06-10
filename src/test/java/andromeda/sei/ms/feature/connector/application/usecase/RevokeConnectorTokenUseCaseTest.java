package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenNotFoundException;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenRevokedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeConnectorTokenUseCaseTest {

    @Mock
    private ConnectorTokenRepository connectorTokenRepository;

    @InjectMocks
    private RevokeConnectorTokenUseCase useCase;

    @Test
    void shouldRevokeActiveToken() {
        ConnectorToken token = activeToken();
        when(connectorTokenRepository.findByIdAndOrganizationId("token-1", "org-1"))
                .thenReturn(Optional.of(token));
        when(connectorTokenRepository.save(any())).thenReturn(token);
        ArgumentCaptor<ConnectorToken> captor = ArgumentCaptor.forClass(ConnectorToken.class);

        useCase.execute("token-1", "org-1");

        verify(connectorTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void shouldThrowNotFoundWhenTokenDoesNotExist() {
        when(connectorTokenRepository.findByIdAndOrganizationId("token-1", "org-1"))
                .thenReturn(Optional.empty());

        var ex = assertThrows(ConnectorTokenNotFoundException.class,
                () -> useCase.execute("token-1", "org-1"));
        assertThat(ex.getCode()).isEqualTo("CONNECTOR_TOKEN_NOT_FOUND");
    }

    @Test
    void shouldThrowRevokedExceptionWhenTokenAlreadyInactive() {
        ConnectorToken token = activeToken();
        token.setActive(false);
        when(connectorTokenRepository.findByIdAndOrganizationId("token-1", "org-1"))
                .thenReturn(Optional.of(token));

        var ex = assertThrows(ConnectorTokenRevokedException.class,
                () -> useCase.execute("token-1", "org-1"));
        assertThat(ex.getCode()).isEqualTo("CONNECTOR_TOKEN_REVOKED");
        verify(connectorTokenRepository, never()).save(any());
    }

    private ConnectorToken activeToken() {
        Instant now = Instant.now();
        return new ConnectorToken("token-1", "org-1", "user-1",
                "token-abcdefgh", "hashvalue", true, 0L, now, now);
    }
}
