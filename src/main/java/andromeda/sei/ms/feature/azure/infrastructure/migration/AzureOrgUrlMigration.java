package andromeda.sei.ms.feature.azure.infrastructure.migration;

import andromeda.sei.ms.feature.azure.domain.AzureOrgUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One-time migration for azure-repos-provider D7 (task 1.3). OAuth connections used to store the
 * Entra token issuer ({@code iss}) as {@code azureOrgUrl} — not an Azure DevOps organization URL,
 * so every Git (and Boards) call through them targeted the wrong host.
 *
 * <p>Active OAuth connections (or legacy ones without {@code authMethod}, which predate PAT
 * support and were OAuth) whose URL is not {@code https://dev.azure.com/{org}} /
 * {@code https://{org}.visualstudio.com} become {@code status = ERROR},
 * {@code lastErrorCode = AZURE_ORG_URL_INVALID}, with a message asking for the URL. The tokens are
 * kept: {@code PUT /api/v1/integrations/azure/connection/org-url} fixes the URL without
 * reauthorizing. The scheduled sync skips connections in {@code ERROR}.
 *
 * <p>Idempotent: a connection already flagged is skipped, a fixed one no longer matches.
 */
@Slf4j
@Component
@Order(27)
@RequiredArgsConstructor
public class AzureOrgUrlMigration implements ApplicationRunner {

    static final String COLLECTION = "azure_connections";
    static final String ERROR_CODE = "AZURE_ORG_URL_INVALID";
    static final String ERROR_MESSAGE = "Informe a URL da organização do Azure DevOps (https://dev.azure.com/{org}) "
            + "para voltar a sincronizar; não é preciso reautorizar.";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Query oauthConnections = Query.query(Criteria.where("active").is(true)
                .and("authMethod").in(Arrays.asList("OAUTH", null)));
        oauthConnections.fields().include("_id", "azureOrgUrl", "lastErrorCode");
        List<Document> connections = mongoTemplate.find(oauthConnections, Document.class, COLLECTION);

        List<Object> flagged = new ArrayList<>();
        for (Document connection : connections) {
            if (AzureOrgUrl.isValid(connection.getString("azureOrgUrl"))) continue;
            if (ERROR_CODE.equals(connection.getString("lastErrorCode"))) continue;
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(connection.get("_id"))),
                    new Update().set("status", "ERROR").set("lastErrorCode", ERROR_CODE)
                            .set("lastErrorMessage", ERROR_MESSAGE).set("updatedAt", Instant.now()),
                    COLLECTION);
            flagged.add(connection.get("_id"));
        }
        log.info("AzureOrgUrlMigration: {} OAuth connection(s) checked, {} flagged {} ({})", connections.size(),
                flagged.size(), ERROR_CODE, flagged);
    }
}
