package andromeda.sei.ms.feature.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Ticket de uso unico (perfil-integracoes D1) que autoriza abrir o popup OAuth sem JWT.
 * So o hash SHA-256 do ticket e guardado; o ticket em claro vai uma unica vez ao cliente.
 * Dado efemero: expira sozinho (TTL em expiresAt), excecao documentada ao soft delete.
 */
public class OAuthLinkTicket {

    private final String id;
    private final String organizationId;
    private final String userId;
    private final String ticketHash;
    private final OAuthLinkPurpose purpose;
    private final String azureOrgUrl;
    private final Instant expiresAt;
    private final Instant consumedAt;
    private final Instant createdAt;

    public OAuthLinkTicket(String id, String organizationId, String userId, String ticketHash,
                           OAuthLinkPurpose purpose, String azureOrgUrl, Instant expiresAt,
                           Instant consumedAt, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.ticketHash = ticketHash;
        this.purpose = purpose;
        this.azureOrgUrl = azureOrgUrl;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.createdAt = createdAt;
    }

    public static OAuthLinkTicket issue(String organizationId, String userId, String ticketHash,
                                        OAuthLinkPurpose purpose, String azureOrgUrl,
                                        Instant now, long ttlSeconds) {
        if (purpose == null) {
            throw new IllegalArgumentException("purpose is required");
        }
        boolean azure = purpose == OAuthLinkPurpose.AZURE_ORG_CONNECT;
        if (azure && (azureOrgUrl == null || azureOrgUrl.isBlank())) {
            throw new IllegalArgumentException("azureOrgUrl is required for AZURE_ORG_CONNECT");
        }
        return new OAuthLinkTicket(null, organizationId, userId, ticketHash, purpose,
                azure ? azureOrgUrl : null, now.plusSeconds(ttlSeconds), null, now);
    }

    /** SHA-256 em hexadecimal minusculo. */
    public static String hash(String rawTicket) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawTicket.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getUserId() { return userId; }
    public String getTicketHash() { return ticketHash; }
    public OAuthLinkPurpose getPurpose() { return purpose; }
    public String getAzureOrgUrl() { return azureOrgUrl; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
