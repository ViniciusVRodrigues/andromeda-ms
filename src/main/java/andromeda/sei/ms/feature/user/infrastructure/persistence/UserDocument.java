package andromeda.sei.ms.feature.user.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "users")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'active': 1}"),
        @CompoundIndex(def = "{'organizationId': 1, 'azureId': 1}", unique = true, sparse = true, name = "uq_user_org_azureId"),
        /* E-mail é único entre usuários ATIVOS, não globalmente — que é exatamente a regra
         * que o código sempre aplicou (UserRepositoryImpl usa existsByEmailAndActiveTrue).
         * O índice global anterior era mais restritivo que o sistema: como a remoção é
         * soft delete (DeactivateUserUseCase apenas marca active=false, preservando o
         * e-mail), ele tornaria impossível reconvidar alguém que já foi removido, e não
         * podia sequer ser criado numa base que já acumulou desativados com o mesmo e-mail.
         * partialFilterExpression restringe o índice às linhas ativas. */
        @CompoundIndex(def = "{'email': 1}", unique = true, name = "uq_user_email_active",
                partialFilter = "{'active': true}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {

    @Id
    private String id;

    private String organizationId;

    /* Sem @Indexed(unique = true): a unicidade vale só entre ativos e é declarada como
     * índice parcial em @CompoundIndexes acima. */
    private String email;

    private String passwordHash;
    private String name;
    private String roleId;
    private String seniority;
    private String githubId;
    private String azureId;
    private String azureEmail;
    private String inviteToken;
    private boolean confirmed;
    private boolean active = true;

    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
}
