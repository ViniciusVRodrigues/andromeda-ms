package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.AllocationPolicy;
import andromeda.sei.ms.feature.organization.domain.Organization;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationPolicyMapperTest {

    private final OrganizationDocumentMapper mapper = new OrganizationDocumentMapper();

    @Test
    void should_readDefault15_whenTheDocumentHasNoPolicy() {
        Organization organization = mapper.toDomain(OrganizationDocument.builder().id("org-1").active(true).build());

        assertThat(organization.getAllocationPolicy()).isEqualTo(AllocationPolicy.defaults());
        assertThat(organization.getAllocationPolicy().outOfDeliveryCapPercent()).isEqualTo(15);
    }

    @Test
    void should_roundTripTheCap() {
        Instant now = Instant.now();
        Organization organization = new Organization(null, "Org", "org", null, null, null, null, null,
                true, null, now, now);
        organization.setAllocationPolicy(new AllocationPolicy(0));

        Organization back = mapper.toDomain(mapper.toDocument(organization));

        assertThat(back.getAllocationPolicy().outOfDeliveryCapPercent()).isZero();
        assertThat(mapper.toDocument(organization).getAllocationPolicy().getOutOfDeliveryCapPercent()).isZero();
    }
}
