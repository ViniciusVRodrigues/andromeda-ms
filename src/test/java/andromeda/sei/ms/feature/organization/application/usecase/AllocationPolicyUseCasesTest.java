package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.AllocationPolicyOutput;
import andromeda.sei.ms.feature.organization.domain.AllocationPolicy;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationPolicyUseCasesTest {

    private static final String ORG = "org-1";

    @Mock private OrganizationRepository organizationRepository;
    @InjectMocks private UpdateAllocationPolicyUseCase updateUseCase;
    @InjectMocks private GetAllocationPolicyUseCase getUseCase;

    private Organization organization() {
        Instant now = Instant.now();
        return new Organization(ORG, "Org", "org", null, null, null, null, null, true, 1L, now, now);
    }

    @Test
    void should_returnDefault15_when_neverConfigured() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));

        assertThat(getUseCase.execute(ORG)).isEqualTo(new AllocationPolicyOutput(15));
        assertThat(AllocationPolicy.defaults().outOfDeliveryCapPercent()).isEqualTo(15);
    }

    @Test
    void should_persistTheCap() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));
        when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(updateUseCase.execute(ORG, 40)).isEqualTo(new AllocationPolicyOutput(40));
    }

    @Test
    void should_failWith404_when_organizationDoesNotExist() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getUseCase.execute(ORG)).isInstanceOf(OrganizationNotFoundException.class);
        assertThatThrownBy(() -> updateUseCase.execute(ORG, 10)).isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void should_rejectACapOutsideTheRange_inTheDomain() {
        assertThatThrownBy(() -> new AllocationPolicy(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AllocationPolicy(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
