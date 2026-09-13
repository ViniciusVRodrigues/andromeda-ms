package andromeda.sei.ms.feature.connector.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hard delete do uso de IA de uma organizacao.
 *
 * <p>Diferente de Azure e GitHub, este dado <b>nao volta com um re-sync</b>: ele chega por upload
 * do connector. Apagar aqui e perda definitiva, e a UI precisa dizer isso antes de confirmar.
 *
 * <p>Uso de IA e dado individual. {@code scope.userId()} nao-nulo limita a deleção ao proprio
 * usuario — e o que sobra para quem tem apenas {@code CONNECTOR_TOKEN_MANAGE_OWN}. Quem tem
 * {@code CONNECTOR_TOKEN_MANAGE_ALL} passa o escopo da organizacao inteira. Essa decisao e tomada
 * no caso de uso a partir do token, nunca por parametro do cliente.
 *
 * <p>{@code fluency_scores} sai junto: e derivado inteiramente de {@code ai_usages}, e preservar
 * o score depois de apagar a origem deixaria um numero orfao, impossivel de recalcular.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConnectorIntegrationDataWiper implements IntegrationDataWiper {

    private final MongoTemplate mongoTemplate;

    @Override
    public Integration integration() {
        return Integration.AI_CONNECTOR;
    }

    @Override
    public Map<String, Long> wipe(String organizationId, WipeScope scope) {
        Map<String, Long> removidos = new LinkedHashMap<>();

        removidos.put("ai_usage_allocations",
                mongoTemplate.remove(query(organizationId, scope), "ai_usage_allocations").getDeletedCount());
        removidos.put("ai_usages",
                mongoTemplate.remove(query(organizationId, scope), "ai_usages").getDeletedCount());
        removidos.put("fluency_scores",
                mongoTemplate.remove(query(organizationId, scope), "fluency_scores").getDeletedCount());

        log.info("Wipe conector IA concluido: org={} scope={} removidos={}", organizationId, scope, removidos);
        return removidos;
    }

    private Query query(String organizationId, WipeScope scope) {
        Criteria c = Criteria.where("organizationId").is(organizationId);
        if (scope.userId() != null) c = c.and("userId").is(scope.userId());
        return Query.query(c);
    }
}
