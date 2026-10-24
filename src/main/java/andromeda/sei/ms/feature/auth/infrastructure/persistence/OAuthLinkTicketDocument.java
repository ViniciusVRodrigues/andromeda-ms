package andromeda.sei.ms.feature.auth.infrastructure.persistence;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Excecao documentada ao soft delete (andromeda-ms-migrations): dado efemero de seguranca. O TTL
 * em expiresAt (expireAfter = 0) apaga o documento assim que vence.
 */
@Document(collection = "oauth_link_tickets")
@Getter
@Setter
@Builder
public class OAuthLinkTicketDocument {

    @Id
    private String id;

    private String organizationId;
    private String userId;

    /** SHA-256 do ticket: o ticket em claro nunca e persistido. */
    @Indexed(unique = true)
    private String ticketHash;

    private String purpose;
    private String azureOrgUrl;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    private Instant consumedAt;

    /** Sempre true (convencao); o ciclo de vida e o TTL. */
    private boolean active;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}
