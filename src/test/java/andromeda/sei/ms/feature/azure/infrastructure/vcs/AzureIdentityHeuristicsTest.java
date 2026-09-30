package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tasks 4.4/4.5: identity id normalization and service-account detection. */
class AzureIdentityHeuristicsTest {

    @Test
    void shouldNormalizeGuidsToLowercaseHyphenated() {
        assertThat(AzureIdentityIds.normalize("2428198325304a9caeb788d60d57acfd"))
                .isEqualTo("24281983-2530-4a9c-aeb7-88d60d57acfd");
        assertThat(AzureIdentityIds.normalize("D6245F20-2AF8-44F4-9451-8107CB2767DB"))
                .isEqualTo("d6245f20-2af8-44f4-9451-8107cb2767db");
        assertThat(AzureIdentityIds.normalize("{d6245f20-2af8-44f4-9451-8107cb2767db}"))
                .isEqualTo("d6245f20-2af8-44f4-9451-8107cb2767db");
        assertThat(AzureIdentityIds.normalize(" not-a-guid ")).isEqualTo("not-a-guid");
        assertThat(AzureIdentityIds.normalize("  ")).isNull();
        assertThat(AzureIdentityIds.normalize(null)).isNull();
    }

    @Test
    void shouldDetectContainersBuildServicesAndCollectionAccounts() {
        assertThat(detect("id", null, "[2016_10_31]\\2016_10_31 Team", true)).isTrue();
        assertThat(detect("id", "Build\\9f2c0000-0000-0000-0000-000000000000", "Payments Build Service (acme)", false))
                .isTrue();
        assertThat(detect("id", "Build\\9f2c0000-0000-0000-0000-000000000000", "Anything", false)).isTrue();
        assertThat(detect("id", null, "Project Collection Build Service (acme)", false)).isTrue();
        assertThat(detect("id", null, "[DefaultCollection]\\Project Collection Service Accounts", false)).isTrue();
        assertThat(detect("id", "ana@acme.com", "Ana Souza", false)).isFalse();
    }

    @Test
    void shouldMatchTheOrganizationListByIdEmailOrName() {
        List<String> list = List.of("D6245F202AF844F494518107CB2767DB", "deploy@acme.com", "Release Pipeline");

        assertThat(AzureServiceIdentityDetector.isServiceIdentity("d6245f20-2af8-44f4-9451-8107cb2767db", null,
                "Someone", false, list)).isTrue();
        assertThat(AzureServiceIdentityDetector.isServiceIdentity("x", "Deploy@Acme.com", "Someone", false, list))
                .isTrue();
        assertThat(AzureServiceIdentityDetector.isServiceIdentity("x", null, "release pipeline", false, list)).isTrue();
        assertThat(AzureServiceIdentityDetector.isServiceIdentity("x", "ana@acme.com", "Ana", false, list)).isFalse();
    }

    private static boolean detect(String id, String uniqueName, String displayName, boolean container) {
        return AzureServiceIdentityDetector.isServiceIdentity(id, uniqueName, displayName, container, List.of());
    }
}
