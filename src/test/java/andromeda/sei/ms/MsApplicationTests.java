package andromeda.sei.ms;

import andromeda.sei.ms.shared.testsupport.MongoTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

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

	@Test
	void contextLoads() {
	}

}
