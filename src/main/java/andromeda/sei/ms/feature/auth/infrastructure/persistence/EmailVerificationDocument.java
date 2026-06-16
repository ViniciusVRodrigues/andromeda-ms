package andromeda.sei.ms.feature.auth.infrastructure.persistence;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "email_verifications")
@Getter
@Setter
@Builder
public class EmailVerificationDocument {

    @Id
    private String id;

    private String email;
    private String code;
    private boolean used;

    @Indexed(expireAfter = "15m")
    private Instant createdAt;
}
