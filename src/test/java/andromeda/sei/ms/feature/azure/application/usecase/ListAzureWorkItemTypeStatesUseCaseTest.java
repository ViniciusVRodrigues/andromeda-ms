package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureIntrospectionFailedException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureWorkItemTypeNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AdoWorkItemTypeStateDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsClient;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAzureWorkItemTypeStatesUseCaseTest {

    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureAuthStrategyResolver authStrategyResolver;
    @Mock private AzureDevOpsClient azureDevOpsClient;

    @InjectMocks
    private ListAzureWorkItemTypeStatesUseCase useCase;

    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnStatesForValidBindingAndType() {
        AzureProjectBinding binding = binding();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection()));
        when(authStrategyResolver.resolve(any()))
                .thenReturn("Bearer token");
        when(azureDevOpsClient.getWorkItemTypeStates("https://dev.azure.com/org", "azure-1", "Bug", "Bearer token"))
                .thenReturn(List.of(new AdoWorkItemTypeStateDto("New", "b2b2b2", "Proposed")));

        var result = useCase.execute("bind-1", "Bug");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("New");
    }

    @Test
    void shouldThrowBindingNotFoundWhenBindingMissing() {
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bad-id", "org-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("bad-id", "Bug"))
                .isInstanceOf(AzureBindingNotFoundException.class);
    }

    @Test
    void shouldThrowWorkItemTypeNotFoundWhenAzureReturns404() {
        AzureProjectBinding binding = binding();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection()));
        when(authStrategyResolver.resolve(any()))
                .thenReturn("Bearer token");
        when(azureDevOpsClient.getWorkItemTypeStates("https://dev.azure.com/org", "azure-1", "Unknown", "Bearer token"))
                .thenThrow(WebClientResponseException.create(404, "Not Found", null, null, null));

        assertThatThrownBy(() -> useCase.execute("bind-1", "Unknown"))
                .isInstanceOf(AzureWorkItemTypeNotFoundException.class);
    }

    @Test
    void shouldThrowIntrospectionFailedOnOtherAzureError() {
        AzureProjectBinding binding = binding();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection()));
        when(authStrategyResolver.resolve(any()))
                .thenReturn("Bearer token");
        when(azureDevOpsClient.getWorkItemTypeStates("https://dev.azure.com/org", "azure-1", "Bug", "Bearer token"))
                .thenThrow(WebClientResponseException.create(500, "Server Error", null, null, null));

        assertThatThrownBy(() -> useCase.execute("bind-1", "Bug"))
                .isInstanceOf(AzureIntrospectionFailedException.class);
    }

    private AzureProjectBinding binding() {
        return new AzureProjectBinding("bind-1", "org-1", "proj-1", "azure-1", "Azure Proj",
                true, null, null, true, NOW, NOW, null);
    }

    private AzureConnection connection() {
        return new AzureConnection("conn-1", "org-1", "https://dev.azure.com/org",
                "enc-access", "enc-refresh", NOW.plusSeconds(3600),
                "user-1", AzureConnectionStatus.CONNECTED, null, 0, null, null, true, NOW, NOW, null,
                AzureAuthMethod.OAUTH, null);
    }
}
