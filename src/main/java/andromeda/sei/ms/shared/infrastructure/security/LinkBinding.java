package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;

import java.io.Serializable;

/**
 * Vinculo do fluxo OAuth guardado na sessao HTTP quando o ticket de uso unico e consumido
 * (perfil-integracoes D1). So o hash do nonce fica aqui; o nonce em claro vai ao navegador no cookie.
 */
public record LinkBinding(String organizationId, String userId, OAuthLinkPurpose purpose,
                          String azureOrgUrl, String nonceHash) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "ANDROMEDA_OAUTH_LINK_BINDING";
}
