package andromeda.sei.ms.shared.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AzureOAuthOrgUrlStateTest {

    @Test
    void roundTrips() {
        String state = AzureOAuthOrgUrlState.encode("abc_-=", "https://acme.visualstudio.com");

        assertThat(AzureOAuthOrgUrlState.decode(state)).contains("https://acme.visualstudio.com");
    }

    @Test
    void stateWithoutAnOrgUrl_decodesToEmpty() {
        assertThat(AzureOAuthOrgUrlState.decode("no-separator")).isEmpty();
        assertThat(AzureOAuthOrgUrlState.decode("x.")).isEmpty();
        assertThat(AzureOAuthOrgUrlState.decode(null)).isEmpty();
    }

    @Test
    void tamperedSuffix_isRefusedOrChangesTheValue() {
        assertThat(AzureOAuthOrgUrlState.decode("x.***")).isEmpty();
        String state = AzureOAuthOrgUrlState.encode("s", "https://dev.azure.com/acme");
        String forged = AzureOAuthOrgUrlState.encode("s", "https://dev.azure.com/evil");

        assertThat(forged).isNotEqualTo(state);
        assertThat(AzureOAuthOrgUrlState.decode(forged)).contains("https://dev.azure.com/evil");
    }
}
