package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.AzureConnectionStatusOutput;
import andromeda.sei.ms.feature.azure.application.dto.AzureOAuthCallbackInput;
import andromeda.sei.ms.feature.azure.application.port.AzureDevOpsAccessProbe;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureTokenRefreshFailedException;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tasks 1.2/1.3: organization URL informed at OAuth start, validated after the callback, fixable later. */
@ExtendWith(MockitoExtension.class)
class AzureOrgUrlFlowUseCasesTest {

    private static final String ORG = "org-1";
    private static final String URL = "https://dev.azure.com/acme";

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private AzureDevOpsAccessProbe accessProbe;
    @Mock private SyncAzureOrgLevelDataUseCase syncAzureOrgLevelDataUseCase;
    @Mock private AzureTokenRefreshService azureTokenRefreshService;

    @BeforeEach
    void setUp() {
        lenient().when(encryptionService.encrypt(any())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        lenient().when(azureConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class Start {

        private final StartAzureOAuthUseCase useCase = new StartAzureOAuthUseCase();

        @Test
        void shouldNormalizeAValidUrl() {
            assertThat(useCase.execute("https://dev.azure.com/acme/")).isEqualTo(URL);
            assertThat(useCase.execute("https://acme.visualstudio.com")).isEqualTo("https://acme.visualstudio.com");
        }

        @Test
        void shouldRejectAMissingOrForeignUrl() {
            assertThatThrownBy(() -> useCase.execute(null)).isInstanceOf(AzureOrgUrlInvalidException.class);
            assertThatThrownBy(() -> useCase.execute("https://login.microsoftonline.com/tenant/v2.0"))
                    .isInstanceOf(AzureOrgUrlInvalidException.class)
                    .hasFieldOrPropertyWithValue("code", "AZURE_ORG_URL_INVALID");
        }
    }

    @Nested
    class Callback {

        private CompleteAzureOAuthConnectionUseCase useCase;

        @BeforeEach
        void setUp() {
            useCase = new CompleteAzureOAuthConnectionUseCase(azureConnectionRepository, encryptionService, accessProbe,
                    syncAzureOrgLevelDataUseCase);
        }

        @Test
        void shouldStoreTheInformedUrlAndMarkConnected() {
            when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.empty());
            when(accessProbe.projects(any())).thenReturn(AzureProjectsAccess.GRANTED);
            when(accessProbe.reposAccess(any())).thenReturn(Boolean.TRUE);

            AzureConnection saved = useCase.execute(input(URL + "/"));

            assertThat(saved.getAzureOrgUrl()).isEqualTo(URL);
            assertThat(saved.getStatus()).isEqualTo(AzureConnectionStatus.CONNECTED);
            assertThat(saved.getAuthMethod()).isEqualTo(AzureAuthMethod.OAUTH);
            assertThat(saved.getAccessToken()).isEqualTo("enc:access");
            assertThat(saved.getRefreshToken()).isEqualTo("enc:refresh");
            assertThat(saved.getAuthorizedByUserId()).isEqualTo("user-1");
            assertThat(saved.getReposAccess()).isTrue();
            verify(syncAzureOrgLevelDataUseCase).execute(saved);
        }

        @Test
        void shouldPersistNothingWithoutAValidUrlInTheState() {
            assertThatThrownBy(() -> useCase.execute(input(null))).isInstanceOf(AzureOrgUrlInvalidException.class);
            assertThatThrownBy(() -> useCase.execute(input("https://login.microsoftonline.com/x/v2.0")))
                    .isInstanceOf(AzureOrgUrlInvalidException.class);

            verify(azureConnectionRepository, never()).save(any());
            verifyNoInteractions(accessProbe);
        }

        @Test
        void shouldKeepTheTokensInErrorWhenTheOrganizationIsNotReachable() {
            when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.empty());
            when(accessProbe.projects(any())).thenReturn(AzureProjectsAccess.DENIED);

            assertThatThrownBy(() -> useCase.execute(input(URL))).isInstanceOf(AzureOrgUrlInvalidException.class);

            ArgumentCaptor<AzureConnection> captor = ArgumentCaptor.forClass(AzureConnection.class);
            verify(azureConnectionRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(AzureConnectionStatus.ERROR);
            assertThat(captor.getValue().getLastErrorCode()).isEqualTo("AZURE_ORG_URL_INVALID");
            verifyNoInteractions(syncAzureOrgLevelDataUseCase);
        }

        @Test
        void shouldNotReplaceAWorkingConnectionWithAnUnreachableOne() {
            AzureConnection working = connection(AzureAuthMethod.PAT, AzureConnectionStatus.CONNECTED, URL);
            when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(working));
            when(accessProbe.projects(any())).thenReturn(AzureProjectsAccess.DENIED);

            assertThatThrownBy(() -> useCase.execute(input("https://dev.azure.com/typo")))
                    .isInstanceOf(AzureOrgUrlInvalidException.class);

            verify(azureConnectionRepository, never()).save(any());
        }

        private static AzureOAuthCallbackInput input(String orgUrl) {
            return new AzureOAuthCallbackInput(ORG, "user-1", orgUrl, "access", "refresh", Instant.now().plusSeconds(3600));
        }
    }

    @Nested
    class Fix {

        private UpdateAzureOrgUrlUseCase useCase;

        @BeforeEach
        void setUp() {
            TenantContextHolder.set(ORG);
            useCase = new UpdateAzureOrgUrlUseCase(azureConnectionRepository, azureTokenRefreshService, accessProbe,
                    syncAzureOrgLevelDataUseCase);
        }

        @AfterEach
        void tearDown() {
            TenantContextHolder.clear();
        }

        @Test
        void shouldFixTheUrlWithoutReauthorizing() {
            AzureConnection broken = connection(AzureAuthMethod.OAUTH, AzureConnectionStatus.ERROR,
                    "https://login.microsoftonline.com/tenant/v2.0");
            broken.setLastErrorCode("AZURE_ORG_URL_INVALID");
            when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(broken));
            when(azureTokenRefreshService.refreshIfNeeded(broken)).thenReturn(broken);
            when(accessProbe.projects(broken)).thenReturn(AzureProjectsAccess.GRANTED);
            when(accessProbe.reposAccess(broken)).thenReturn(Boolean.FALSE);

            AzureConnectionStatusOutput output = useCase.execute(URL + "/");

            assertThat(output.azureOrgUrl()).isEqualTo(URL);
            assertThat(output.status()).isEqualTo(AzureConnectionStatus.CONNECTED);
            assertThat(output.lastErrorCode()).isNull();
            assertThat(output.reposAccess()).isFalse();
            assertThat(broken.getAccessToken()).isEqualTo("enc-access");
            verify(azureConnectionRepository).save(broken);
        }

        @Test
        void shouldRejectAUrlTheCredentialCannotReach() {
            AzureConnection pat = connection(AzureAuthMethod.PAT, AzureConnectionStatus.CONNECTED, URL);
            when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(pat));
            when(accessProbe.projects(pat)).thenReturn(AzureProjectsAccess.DENIED);

            assertThatThrownBy(() -> useCase.execute("https://dev.azure.com/other"))
                    .isInstanceOf(AzureOrgUrlInvalidException.class);

            assertThat(pat.getAzureOrgUrl()).isEqualTo(URL);
            verify(azureConnectionRepository, never()).save(any());
            verifyNoInteractions(azureTokenRefreshService);
        }

        @Test
        void shouldKeepTheConnectionFlaggedWhenARefreshedTokenStillCannotReachTheNewUrl() {
            AzureConnection broken = connection(AzureAuthMethod.OAUTH, AzureConnectionStatus.ERROR,
                    "https://login.microsoftonline.com/tenant/v2.0");
            broken.setLastErrorCode("AZURE_ORG_URL_INVALID");
            broken.setLastErrorMessage("Informe a URL");
            AzureConnection refreshed = connection(AzureAuthMethod.OAUTH, AzureConnectionStatus.CONNECTED,
                    "https://login.microsoftonline.com/tenant/v2.0");
            refreshed.setLastErrorCode("AZURE_ORG_URL_INVALID");
            when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(broken));
            when(azureTokenRefreshService.refreshIfNeeded(broken)).thenReturn(refreshed);
            when(accessProbe.projects(refreshed)).thenReturn(AzureProjectsAccess.DENIED);

            assertThatThrownBy(() -> useCase.execute("https://dev.azure.com/typo"))
                    .isInstanceOf(AzureOrgUrlInvalidException.class);

            ArgumentCaptor<AzureConnection> saved = ArgumentCaptor.forClass(AzureConnection.class);
            verify(azureConnectionRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(AzureConnectionStatus.ERROR);
            assertThat(saved.getValue().getLastErrorCode()).isEqualTo("AZURE_ORG_URL_INVALID");
            assertThat(saved.getValue().getLastErrorMessage()).isEqualTo("Informe a URL");
            assertThat(saved.getValue().getAzureOrgUrl()).isEqualTo("https://login.microsoftonline.com/tenant/v2.0");
        }

        @Test
        void shouldRejectAMalformedUrlBeforeLookingUpTheConnection() {
            assertThatThrownBy(() -> useCase.execute("acme")).isInstanceOf(AzureOrgUrlInvalidException.class);

            verifyNoInteractions(azureConnectionRepository);
        }

        @Test
        void shouldAskToReconnectWhenTheOAuthTokenCannotBeRefreshed() {
            AzureConnection oauth = connection(AzureAuthMethod.OAUTH, AzureConnectionStatus.ERROR, "bad");
            when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(oauth));
            when(azureTokenRefreshService.refreshIfNeeded(oauth)).thenThrow(new RuntimeException("expired"));

            assertThatThrownBy(() -> useCase.execute(URL)).isInstanceOf(AzureTokenRefreshFailedException.class);
        }
    }

    private static AzureConnection connection(AzureAuthMethod method, AzureConnectionStatus status, String url) {
        return new AzureConnection("c-1", ORG, url, "enc-access", "enc-refresh", Instant.now().plusSeconds(3600),
                "user-1", status, null, 3, null, null, true, Instant.now(), Instant.now(), 1L, method, "enc-pat");
    }
}
