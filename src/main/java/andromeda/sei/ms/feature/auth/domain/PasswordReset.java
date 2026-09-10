package andromeda.sei.ms.feature.auth.domain;

import java.time.Instant;

/**
 * Token de redefinicao de senha.
 *
 * Entidade propria em vez de reuso de EmailVerification: aquela nao tem prazo
 * de validade (so o booleano used) e guarda um codigo de 6 digitos, fraco
 * demais para um link que redefine a senha sozinho. Compartilhar a colecao
 * faria um codigo de verificacao de e-mail valer como token de redefinicao.
 *
 * O campo tokenHash guarda o hash do token, nunca o token em si: vazamento da
 * colecao nao entrega links utilizaveis.
 */
public class PasswordReset {

    private String id;
    private String email;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;

    public PasswordReset(String id, String email, String tokenHash,
                         Instant expiresAt, Instant usedAt, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
    }

    /** Vale apenas enquanto nao foi usado e nao passou do prazo. */
    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt != null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant when) {
        this.usedAt = when;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
