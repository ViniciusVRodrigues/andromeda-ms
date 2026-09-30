package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzurePatConnectionInput;
import andromeda.sei.ms.feature.azure.application.port.AzureDevOpsAccessProbe;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigureAzureDevOpsPatUseCaseTest {

    @Mock
    private AzureConnectionRepository azureConnectionRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private SyncAzureOrgLevelDataUseCase syncAzureOrgLevelDataUseCase;

    @Mock
    private AzureDevOpsAccessProbe accessProbe;

    @InjectMocks
    private ConfigureAzureDevOpsPatUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        lenient().when(accessProbe.projects(any())).thenReturn(AzureProjectsAccess.GRANTED);
        lenient().when(accessProbe.reposAccess(any())).thenReturn(Boolean.TRUE);
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
                .isInstanceOf(AzureOrgUrlInvalidException.class);

        verify(azureConnectionRepository, never()).save(any());
    }

    @Test
    void execute_shouldAcceptDevAzureComUrlOnCreate() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("myPat")).thenReturn("encryptedPat");
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new AzurePatConnectionInput("https://dev.azure.com/myorg", "myPat"));

        verify(azureConnectionRepository).save(any());
    }

    @Test
    void execute_shouldAcceptVisualStudioComUrlOnCreate() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("myPat")).thenReturn("encryptedPat");
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new AzurePatConnectionInput("https://myorg.visualstudio.com", "myPat"));

        ArgumentCaptor<AzureConnection> captor = ArgumentCaptor.forClass(AzureConnection.class);
        verify(azureConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getAzureOrgUrl()).isEqualTo("https://myorg.visualstudio.com");
    }

    @Test
    void execute_shouldRejectMalformedOrgUrlOnCreate() {
        assertThatThrownBy(() ->
                useCase.execute(new AzurePatConnectionInput("ghp_aFakePersonalAccessTokenValue1234567890", "myPat")))
                .isInstanceOf(AzureOrgUrlInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "AZURE_ORG_URL_INVALID");

        verify(azureConnectionRepository, never()).save(any());
    }

    @Test
    void execute_shouldRejectMalformedOrgUrlOnUpdate_andNotSave() {
        // Validation runs before the repository lookup, so no stub is needed for
        // findByOrganizationIdAndActiveTrue here — the update path is short-circuited
        // before it would ever be reached.
        assertThatThrownBy(() ->
                useCase.execute(new AzurePatConnectionInput("ghp_aFakePersonalAccessTokenValue1234567890", "newPat")))
                .isInstanceOf(AzureOrgUrlInvalidException.class);

        verify(azureConnectionRepository, never()).findByOrganizationIdAndActiveTrue(any());
        verify(azureConnectionRepository, never()).save(any());
    }

    @Test
    void execute_shouldTriggerOrgLevelSyncAfterSuccessfulConnection() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("myPat")).thenReturn("encryptedPat");
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new AzurePatConnectionInput("https://dev.azure.com/myorg", "myPat"));

        verify(syncAzureOrgLevelDataUseCase).execute(any(AzureConnection.class));
    }

    @Test
    void execute_shouldNotFailConnectionWhenOrgLevelSyncThrows() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.empty());
        when(encryptionService.encrypt("myPat")).thenReturn("encryptedPat");
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("ADO unavailable"))
                .when(syncAzureOrgLevelDataUseCase).execute(any(AzureConnection.class));

        useCase.execute(new AzurePatConnectionInput("https://dev.azure.com/myorg", "myPat"));

        ArgumentCaptor<AzureConnection> captor = ArgumentCaptor.forClass(AzureConnection.class);
        verify(azureConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AzureConnectionStatus.CONNECTED);
    }

    @Test
    void execute_shouldRejectAPatDeniedByAzureDevOps_andPersistNothing() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1")).thenReturn(Optional.empty());
        when(encryptionService.encrypt("revokedPat")).thenReturn("encryptedRevoked");
        when(accessProbe.projects(any())).thenReturn(AzureProjectsAccess.DENIED);

        assertThatThrownBy(() -> useCase.execute(new AzurePatConnectionInput("https://dev.azure.com/myorg", "revokedPat")))
                .isInstanceOf(AzurePatInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "AZURE_PAT_INVALID");

        verify(azureConnectionRepository, never()).save(any());
        verify(syncAzureOrgLevelDataUseCase, never()).execute(any());
    }

    @Test
    void execute_shouldAcceptAPatWithoutCodeRead_andRecordReposAccessFalse() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1")).thenReturn(Optional.empty());
        when(encryptionService.encrypt("boardsOnly")).thenReturn("encryptedBoardsOnly");
        when(accessProbe.reposAccess(any())).thenReturn(Boolean.FALSE);
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new AzurePatConnectionInput("https://dev.azure.com/myorg", "boardsOnly"));

        ArgumentCaptor<AzureConnection> captor = ArgumentCaptor.forClass(AzureConnection.class);
        verify(azureConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getReposAccess()).isFalse();
        assertThat(captor.getValue().getStatus()).isEqualTo(AzureConnectionStatus.CONNECTED);
    }

    @Test
    void execute_shouldRecordReposAccessTrueForAFullPat() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1")).thenReturn(Optional.empty());
        when(encryptionService.encrypt("fullPat")).thenReturn("encryptedFull");
        when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new AzurePatConnectionInput("https://dev.azure.com/myorg", "fullPat"));

        ArgumentCaptor<AzureConnection> captor = ArgumentCaptor.forClass(AzureConnection.class);
        verify(azureConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getReposAccess()).isTrue();
    }
}
