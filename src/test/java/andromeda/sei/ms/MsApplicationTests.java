package andromeda.sei.ms;

import andromeda.sei.ms.shared.testsupport.MongoTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Sobe o contexto completo contra o Mongo do Testcontainers. Os segredos obrigatorios do
 * application.yaml recebem valores descartaveis de teste (nenhum segredo real, nenhum .env).
 */
@SpringBootTest(properties = {
        "security.jwt.secret=test-only-jwt-secret-with-at-least-64-bytes-0123456789-0123456789-0123456789",
        "azure.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(MongoTestcontainersConfig.class)
class MsApplicationTests {

	/* O MongoConfig le spring.data.mongodb.uri por @Value (nao ve o @ServiceConnection): sem isto o
	 * contexto iria para o Mongo de desenvolvimento do application.yaml (localhost:27018). */
	@DynamicPropertySource
	static void mongoUri(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", MongoTestcontainersConfig::connectionString);
	}

	@Test
	void contextLoads() {
	}

}
