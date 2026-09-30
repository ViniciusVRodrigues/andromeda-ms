package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.GithubPatConnectionInput;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.exception.GithubPatInvalidException;
import andromeda.sei.ms.feature.github.infrastructure.client.GhUserDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Optional;

/**
 * Validates the submitted PAT against {@code GET /user}, encrypts it, and persists the
 * connection -- requires {@code REPOS_CONFIGURE} (enforced at the controller).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigureGithubConnectionUseCase {

    private final GithubConnectionRepository githubConnectionRepository;
    private final GithubApiClient githubApiClient;
    private final EncryptionService encryptionService;

    public void execute(GithubPatConnectionInput input) {
        if (input.personalAccessToken() == null || input.personalAccessToken().isBlank()) {
            throw new GithubPatInvalidException("personalAccessToken must not be blank");
        }

        String orgId = TenantContextHolder.get();
        String authHeader = "Bearer " + input.personalAccessToken();

        GhUserDto user;
        try {
            user = githubApiClient.getAuthenticatedUser(authHeader);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new GithubPatInvalidException("GitHub rejected the provided personal access token");
            }
            throw e;
        }
        if (user == null) {
            throw new GithubPatInvalidException("GitHub rejected the provided personal access token");
        }

        String encryptedPat = encryptionService.encrypt(input.personalAccessToken());
        Instant now = Instant.now();

        Optional<GithubConnection> existing = githubConnectionRepository.findByOrganizationIdAndActiveTrue(orgId);

        GithubConnection connection;
        if (existing.isPresent()) {
            connection = existing.get();
            connection.setPersonalAccessToken(encryptedPat);
            connection.setStatus(GithubConnectionStatus.CONNECTED);
            connection.setConsecutiveFailures(0);
            connection.setLastErrorCode(null);
            connection.setLastErrorMessage(null);
            connection.setUpdatedAt(now);
        } else {
            connection = new GithubConnection(
                    null, orgId, encryptedPat, null,
                    GithubConnectionStatus.CONNECTED, null, 0, null, null,
                    true, now, now, null);
        }

        githubConnectionRepository.save(connection);
    }
}
