package andromeda.sei.ms.feature.auth.application.usecase;

import andromeda.sei.ms.feature.auth.application.dto.IssueOAuthLinkTicketInput;
import andromeda.sei.ms.feature.auth.application.dto.IssueOAuthLinkTicketOutput;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicketRepository;
import andromeda.sei.ms.feature.auth.domain.exception.OAuthLinkRequestInvalidException;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUrl;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class IssueOAuthLinkTicketUseCase {

    static final long TTL_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthLinkTicketRepository tickets;

    public IssueOAuthLinkTicketOutput execute(IssueOAuthLinkTicketInput input) {
        String azureOrgUrl = null;
        if (input.purpose() == OAuthLinkPurpose.AZURE_ORG_CONNECT) {
            if (input.permissionCodes() == null || !input.permissionCodes().contains("AZURE_CONFIGURE")) {
                throw new ForbiddenOperationException("PERMISSION_DENIED",
                        "AZURE_CONFIGURE is required to connect an Azure DevOps organization");
            }
            if (!AzureOrgUrl.isValid(input.azureOrgUrl())) {
                throw new OAuthLinkRequestInvalidException("AZURE_ORG_URL_INVALID",
                        "azureOrgUrl must match " + AzureOrgUrl.EXPECTED_SHAPE);
            }
            azureOrgUrl = AzureOrgUrl.normalize(input.azureOrgUrl());
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tickets.save(OAuthLinkTicket.issue(input.organizationId(), input.userId(),
                OAuthLinkTicket.hash(raw), input.purpose(), azureOrgUrl, Instant.now(), TTL_SECONDS));
        return new IssueOAuthLinkTicketOutput(raw, TTL_SECONDS);
    }
}
