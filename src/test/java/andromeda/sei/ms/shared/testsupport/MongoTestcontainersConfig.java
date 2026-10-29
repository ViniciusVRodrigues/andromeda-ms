package andromeda.sei.ms.shared.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import andromeda.sei.ms.shared.infrastructure.config.DeclaredIndexInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Mongo real para os testes de persistencia. O container e estatico e compartilhado por todas as
 * classes da JVM de teste (iniciado uma vez; o Ryuk o remove ao fim). O {@link ServiceConnection}
 * sobrescreve {@code spring.data.mongodb.uri}, entao nenhum teste toca o banco de desenvolvimento
 * configurado no application.yaml.
 *
 * <p>O {@link DeclaredIndexInitializer} e um ApplicationRunner (nao entra no slice por scan): importado aqui,
 * roda no startup do contexto e cria os indices declarados, como em producao. Sem ele os testes de
 * unicidade passariam em falso (nenhum indice existiria).
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(DeclaredIndexInitializer.class)
public class MongoTestcontainersConfig {

    static final String MONGO_IMAGE = "mongo:8.0";

    private static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse(MONGO_IMAGE));

    static {
        // DIAGNOSTICO TEMPORARIO (CI do #52): remover depois
        try {
            MONGO.start();
        } catch (RuntimeException e) {
            System.err.println("[tc-diag] start falhou: " + e);
            e.printStackTrace();
        }
        System.err.println("[tc-diag] containerId=" + MONGO.getContainerId());
        try {
            System.err.println("[tc-diag] isRunning=" + MONGO.isRunning());
            System.err.println("[tc-diag] inspect=" + org.testcontainers.DockerClientFactory.instance().client()
                    .inspectContainerCmd(MONGO.getContainerId()).exec().getState());
        } catch (Throwable t) {
            System.err.println("[tc-diag] inspect falhou: " + t);
            t.printStackTrace();
        }
        try {
            System.err.println("[tc-diag] logs=" + MONGO.getLogs());
        } catch (Throwable t) {
            System.err.println("[tc-diag] logs falhou: " + t);
        }
    }

    /**
     * URI do container, para quem le {@code spring.data.mongodb.uri} direto: o {@code MongoConfig}
     * da aplicacao usa {@code @Value} e nao enxerga o {@link ServiceConnection}, entao um
     * {@code @SpringBootTest} precisa sobrescrever a propriedade com {@code @DynamicPropertySource}.
     */
    public static String connectionString() {
        return MONGO.getReplicaSetUrl("andromeda");
    }

    /**
     * {@code destroyMethod = ""}: o container e estatico e compartilhado pela JVM inteira. Sem isso o Spring
     * chama {@code close()} ao tirar um contexto do cache (limite de 32 contextos) e os contextos seguintes
     * falham com "MongoDBContainer should be started first".
     */
    @Bean(destroyMethod = "")
    @ServiceConnection
    MongoDBContainer mongoDBContainer() {
        return MONGO;
    }
}
