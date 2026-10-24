package andromeda.sei.ms.feature.auth.presentation.dto;

/** purpose e String para que um valor desconhecido vire 400 OAUTH_PURPOSE_INVALID, nao erro de JSON. */
public record OAuthLinkTicketRequest(String purpose, String azureOrgUrl) {}
