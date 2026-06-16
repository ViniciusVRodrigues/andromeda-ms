package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzurePatConnectionInput;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.exception.AzurePatInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigureAzureDevOpsPatUseCaseTest {

    @Mock
    private AzureConnectionRepository azureConnectionRepository;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private ConfigureAzureDevOpsPatUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void execute_shouldCreateNewPatConnectionWhenNoneExists() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("myPat")).thenReturn("encryptedPat");
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new AzurePatConnectionInput("https://dev.azure.com/myorg", "myPat"));

        ArgumentCaptor<AzureConnection> captor = ArgumentCaptor.forClass(AzureConnection.class);
        verify(azureConnectionRepository).save(captor.capture());
        AzureConnection saved = captor.getValue();
        assertThat(saved.getAuthMethod()).isEqualTo(AzureAuthMethod.PAT);
        assertThat(saved.getPersonalAccessToken()).isEqualTo("encryptedPat");
        assertThat(saved.getAzureOrgUrl()).isEqualTo("https://dev.azure.com/myorg");
        assertThat(saved.getStatus()).isEqualTo(AzureConnectionStatus.CONNECTED);
        assertThat(saved.getConsecutiveFailures()).isZero();
    }

    @Test
    void execute_shouldReplaceExistingConnectionWithPat() {
        AzureConnection existing = new AzureConnection(
                "conn-1", "org-1", "https://dev.azure.com/old",
                "oldToken", "oldRefresh", Instant.now().plusSeconds(600),
                "user-1", AzureConnectionStatus.CONNECTED,
                null, 2, null, null, true, Instant.now(), Instant.now(), 1L,
                AzureAuthMethod.OAUTH, null);
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(existing));
        when(encryptionService.encrypt("newPat")).thenReturn("encryptedNewPat");
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new AzurePatConnectionInput("https://dev.azure.com/neworg", "newPat"));

        ArgumentCaptor<AzureConnection> captor = ArgumentCaptor.forClass(AzureConnection.class);
        verify(azureConnectionRepository).save(captor.capture());
        AzureConnection saved = captor.getValue();
        assertThat(saved.getAuthMethod()).isEqualTo(AzureAuthMethod.PAT);
        assertThat(saved.getPersonalAccessToken()).isEqualTo("encryptedNewPat");
        assertThat(saved.getAzureOrgUrl()).isEqualTo("https://dev.azure.com/neworg");
        assertThat(saved.getConsecutiveFailures()).isZero();
        assertThat(saved.getAccessToken()).isNull();
        assertThat(saved.getRefreshToken()).isNull();
    }

    @Test
    void execute_shouldThrowWhenPatIsBlank() {
        assertThatThrownBy(() ->
                useCase.execute(new AzurePatConnectionInput("https://dev.azure.com/myorg", "")))
                .isInstanceOf(AzurePatInvalidException.class);

        verify(azureConnectionRepository, never()).save(any());
    }

    @Test
    void execute_shouldThrowWhenOrgUrlIsBlank() {
        assertThatThrownBy(() ->
                useCase.execute(new AzurePatConnectionInput("", "myPat")))
                .isInstanceOf(AzurePatInvalidException.class);

        verify(azureConnectionRepository, never()).save(any());
    }
}
