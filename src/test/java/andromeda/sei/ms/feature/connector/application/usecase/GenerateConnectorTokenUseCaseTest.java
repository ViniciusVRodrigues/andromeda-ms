package andromeda.sei.ms.feature.connector.application.usecase;

import andromeda.sei.ms.feature.connector.application.dto.ConnectorTokenInput;
import andromeda.sei.ms.feature.connector.application.dto.ConnectorTokenOutput;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateConnectorTokenUseCaseTest {

    @Mock
    private ConnectorTokenRepository connectorTokenRepository;

    @InjectMocks
    private GenerateConnectorTokenUseCase useCase;

    @Test
    void shouldGenerateTokenWithHashedValueAndLabel() {
        var input = new ConnectorTokenInput("org-1", "user-1");
        Instant now = Instant.now();
        when(connectorTokenRepository.save(any())).thenAnswer(inv -> {
            ConnectorToken token = inv.getArgument(0);
            token.setId("token-id-1");
            return token;
        });

        ConnectorTokenOutput output = useCase.execute(input);

        assertThat(output.id()).isEqualTo("token-id-1");
        assertThat(output.rawToken()).isNotNull();
        assertThat(output.rawToken()).isNotBlank();
        assertThat(output.label()).startsWith("token-");
        assertThat(output.label()).hasSize(14); // "token-" + 8 chars
    }

    @Test
    void shouldNeverStoreRawTokenInRepository() {
        var input = new ConnectorTokenInput("org-1", "user-1");
        ArgumentCaptor<ConnectorToken> captor = ArgumentCaptor.forClass(ConnectorToken.class);
        when(connectorTokenRepository.save(any())).thenAnswer(inv -> {
            ConnectorToken token = inv.getArgument(0);
            token.setId("token-id-1");
            return token;
        });

        ConnectorTokenOutput output = useCase.execute(input);

        verify(connectorTokenRepository).save(captor.capture());
        ConnectorToken persisted = captor.getValue();
        assertThat(persisted.getTokenHash()).isNotEqualTo(output.rawToken());
        assertThat(persisted.getTokenHash()).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    void shouldSetOrganizationIdAndUserId() {
        var input = new ConnectorTokenInput("org-1", "user-1");
        ArgumentCaptor<ConnectorToken> captor = ArgumentCaptor.forClass(ConnectorToken.class);
        when(connectorTokenRepository.save(any())).thenAnswer(inv -> {
            ConnectorToken token = inv.getArgument(0);
            token.setId("token-id-1");
            return token;
        });

        useCase.execute(input);

        verify(connectorTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganizationId()).isEqualTo("org-1");
        assertThat(captor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(captor.getValue().isActive()).isTrue();
    }
}
