package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSiblingAzureProjectBindingsUseCaseTest {

    @Mock
    private AzureProjectBindingRepository azureProjectBindingRepository;

    @InjectMocks
    private ListSiblingAzureProjectBindingsUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private AzureProjectBinding binding(String id, String azureProjectName) {
        return new AzureProjectBinding(
                id, "org-1", "proj-" + id, "azure-" + id, azureProjectName,
                true, null, null, true, Instant.now(), Instant.now(), null);
    }

    @Test
    void execute_shouldReturnActiveBindingsExcludingGivenBindingId() {
        when(azureProjectBindingRepository.findAllByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(List.of(
                        binding("bind-1", "Project One"),
                        binding("bind-2", "Project Two"),
                        binding("bind-3", "Project Three")));

        List<AzureProjectBinding> siblings = useCase.execute("bind-2");

        assertThat(siblings).extracting(AzureProjectBinding::getId)
                .containsExactly("bind-1", "bind-3");
    }

    @Test
    void execute_shouldReturnEmptyListWhenNoOtherBindingsExist() {
        when(azureProjectBindingRepository.findAllByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(List.of(binding("bind-1", "Project One")));

        List<AzureProjectBinding> siblings = useCase.execute("bind-1");

        assertThat(siblings).isEmpty();
    }
}
