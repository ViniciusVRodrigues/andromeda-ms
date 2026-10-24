package andromeda.sei.ms.feature.auth.presentation;

import andromeda.sei.ms.feature.auth.application.dto.IssueOAuthLinkTicketInput;
import andromeda.sei.ms.feature.auth.application.dto.IssueOAuthLinkTicketOutput;
import andromeda.sei.ms.feature.auth.application.usecase.IssueOAuthLinkTicketUseCase;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.exception.OAuthLinkRequestInvalidException;
import andromeda.sei.ms.feature.auth.presentation.dto.OAuthLinkTicketRequest;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
@RequiredArgsConstructor
public class OAuthLinkTicketController {

    private final IssueOAuthLinkTicketUseCase issueUseCase;

    @Operation(summary = "Emitir ticket de uso unico (60 s) para abrir o popup OAuth",
            description = "Autenticado por JWT. A permissao AZURE_CONFIGURE do proposito AZURE_ORG_CONNECT e conferida no caso de uso.")
    @PostMapping("/link-ticket")
    public ResponseEntity<IssueOAuthLinkTicketOutput> issue(@RequestBody OAuthLinkTicketRequest request,
                                                            @AuthenticationPrincipal AndromedaUserDetails principal) {
        OAuthLinkPurpose purpose = OAuthLinkPurpose.parse(request.purpose())
                .orElseThrow(() -> new OAuthLinkRequestInvalidException("OAUTH_PURPOSE_INVALID",
                        "purpose must be GITHUB_PROFILE or AZURE_ORG_CONNECT"));
        IssueOAuthLinkTicketOutput output = issueUseCase.execute(new IssueOAuthLinkTicketInput(
                principal.getUserId(), principal.getOrganizationId(), principal.getPermissions(),
                purpose, request.azureOrgUrl()));
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }
}
