package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.application.AzureIdentityMatchService;
import andromeda.sei.ms.feature.azure.application.dto.MyAzureIdentityOutput;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureIdentityMatcher;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Read-only state of the caller's own Azure link (perfil-integracoes D12). Writes nothing; the
 * auto-link runs only at the end of the org sync and on account completion. A free single match not
 * linked yet is reported as {@code NOT_FOUND} (the link has not happened), and the response never
 * carries ids or data of another person.
 */
@Component
@RequiredArgsConstructor
public class GetMyAzureIdentityUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final UserRepository userRepository;
    private final AzureOrgUserRepository azureOrgUserRepository;
    private final AzureIdentityMatchService matchService;

    public MyAzureIdentityOutput execute(String organizationId, String userId) {
        if (azureConnectionRepository.findByOrganizationIdAndActiveTrue(organizationId).isEmpty()) {
            return MyAzureIdentityOutput.of("NO_CONNECTION");
        }
        User user = userRepository.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        AzureIdentityMatcher.Status status = user.getAzureId() != null
                ? AzureIdentityMatcher.Status.LINKED
                : matchService.evaluate(user).status();

        return switch (status) {
            case LINKED -> linked(organizationId, user);
            case AMBIGUOUS -> MyAzureIdentityOutput.of("AMBIGUOUS");
            case TAKEN -> MyAzureIdentityOutput.of("TAKEN");
            case NOT_FOUND, MATCH -> MyAzureIdentityOutput.of("NOT_FOUND");
        };
    }

    private MyAzureIdentityOutput linked(String organizationId, User user) {
        AzureOrgUser identity = azureOrgUserRepository
                .findByOrganizationIdAndAzureIdAndActiveTrue(organizationId, user.getAzureId()).orElse(null);
        String email = identity != null && identity.getAzureEmail() != null
                ? identity.getAzureEmail() : user.getAzureEmail();
        String name = identity != null ? identity.getDisplayName() : null;
        return new MyAzureIdentityOutput("LINKED", email, name,
                user.getAzureIdentitySource() == null ? null : user.getAzureIdentitySource().name(),
                user.getAzureIdentityLinkedAt());
    }
}
