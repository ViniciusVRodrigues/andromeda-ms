package andromeda.sei.ms.feature.auth.infrastructure.persistence;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "password_resets")
@Getter
@Setter
@Builder
public class PasswordResetDocument {

    @Id
    private String id;

    private String email;

    /** Hash do token — o token em si nunca e persistido. */
    @Indexed
    private String tokenHash;

    private Instant expiresAt;
    private Instant usedAt;

    /** O Mongo limpa o registro 1h depois; o prazo real e expiresAt (30min). */
    @Indexed(expireAfter = "1h")
    private Instant createdAt;
}
