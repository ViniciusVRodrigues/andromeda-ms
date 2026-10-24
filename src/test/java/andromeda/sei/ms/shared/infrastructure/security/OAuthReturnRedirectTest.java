package andromeda.sei.ms.shared.infrastructure.security;

import org.junit.jupiter.api.Test;

import static andromeda.sei.ms.shared.infrastructure.security.OAuthReturnRedirect.Code;
import static andromeda.sei.ms.shared.infrastructure.security.OAuthReturnRedirect.Provider;
import static andromeda.sei.ms.shared.infrastructure.security.OAuthReturnRedirect.Status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthReturnRedirectTest {

    @Test
    void linked_hasNoCode() {
        assertThat(OAuthReturnRedirect.linked("https://app.example", Provider.GITHUB))
                .isEqualTo("https://app.example/oauth/retorno?provider=github&status=linked");
    }

    @Test
    void connected_azure() {
        assertThat(OAuthReturnRedirect.build("https://app.example", Provider.AZURE, Status.CONNECTED, null))
                .isEqualTo("https://app.example/oauth/retorno?provider=azure&status=connected");
    }

    @Test
    void error_carriesTheClosedCode() {
        for (Code code : Code.values()) {
            assertThat(OAuthReturnRedirect.error("http://localhost:5173", Provider.AZURE, code))
                    .isEqualTo("http://localhost:5173/oauth/retorno?provider=azure&status=error&code=" + code.name());
        }
    }

    @Test
    void trailingSlashOnTheBaseUrl_isHandled() {
        assertThat(OAuthReturnRedirect.linked("https://app.example/", Provider.GITHUB))
                .isEqualTo("https://app.example/oauth/retorno?provider=github&status=linked");
    }

    @Test
    void errorWithoutCode_isRefused() {
        assertThatThrownBy(() -> OAuthReturnRedirect.build("http://x", Provider.GITHUB, Status.ERROR, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownRegistrationId_fallsBackToGithub() {
        assertThat(Provider.fromRegistrationId("azure")).isEqualTo(Provider.AZURE);
        assertThat(Provider.fromRegistrationId("whatever")).isEqualTo(Provider.GITHUB);
        assertThat(Provider.fromRegistrationId(null)).isEqualTo(Provider.GITHUB);
    }
}
