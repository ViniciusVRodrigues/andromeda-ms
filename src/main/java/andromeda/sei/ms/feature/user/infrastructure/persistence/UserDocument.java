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
import java.util.ArrayList;
import java.util.List;

@Document(collection = "users")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'active': 1}"),
        /* Único só entre usuários com azureId. Não pode ser sparse: um índice composto esparso
         * indexa o documento quando QUALQUER chave existe, e organizationId sempre existe — os
         * usuários sem azureId entrariam como null e colidiriam entre si. O filtro $type string
         * exclui tanto o campo ausente (o que o mapper grava hoje, já que o conversor omite nulos)
         * quanto um null explícito. */
        @CompoundIndex(def = "{'organizationId': 1, 'azureId': 1}", unique = true, name = "uq_user_org_azureId",
                partialFilter = "{'azureId': {'$type': 'string'}}"),
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

    /** Per-user overrides on top of the role preset; empty by default (project-scoped-permissions D2). */
    @Builder.Default
    private List<PermissionOverrideDocument> permissionOverrides = new ArrayList<>();
}
