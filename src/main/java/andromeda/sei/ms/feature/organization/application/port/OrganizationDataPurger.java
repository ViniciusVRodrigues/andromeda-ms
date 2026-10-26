package andromeda.sei.ms.feature.organization.application.port;

import java.util.Map;
import java.util.Set;

/**
 * Porta do apagamento das colecoes que pertencem a uma organizacao e que nao sao dados de
 * integracao (esses sao dos {@code IntegrationDataWiper}). Implementada na infraestrutura.
 */
public interface OrganizationDataPurger {

    /**
     * Apaga, por {@code organizationId} exato, todas as colecoes da organizacao.
     *
     * @return quantos documentos foram removidos, por colecao
     */
    Map<String, Long> purge(String organizationId);

    /** Colecoes que o purge cobre; lido pelo teste de arquitetura. */
    Set<String> collections();
}
