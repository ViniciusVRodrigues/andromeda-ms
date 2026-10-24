package andromeda.sei.ms.feature.auth.domain;

import java.util.Optional;

/** Para que serve um ticket de vinculo OAuth. */
public enum OAuthLinkPurpose {
    GITHUB_PROFILE,
    AZURE_ORG_CONNECT;

    /** Valor desconhecido ou ausente devolve vazio (o chamador decide o erro). */
    public static Optional<OAuthLinkPurpose> parse(String value) {
        if (value == null) return Optional.empty();
        for (OAuthLinkPurpose p : values()) {
            if (p.name().equals(value)) return Optional.of(p);
        }
        return Optional.empty();
    }
}
