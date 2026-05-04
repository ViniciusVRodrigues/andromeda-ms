package andromeda.sei.ms.feature.organization.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrganizationTest {

    @Test
    void shouldGenerateSlugFromName() {
        Organization org = Organization.create("Minha Empresa");
        assertThat(org.slug()).isEqualTo("minha-empresa");
    }

    @Test
    void shouldHandleSpecialCharactersInSlug() {
        Organization org = Organization.create("Acme & Corp!");
        assertThat(org.slug()).isEqualTo("acme-corp");
    }

    @Test
    void shouldCollapseMultipleSpaces() {
        Organization org = Organization.create("My   Company  Name");
        assertThat(org.slug()).isEqualTo("my-company-name");
    }
}
