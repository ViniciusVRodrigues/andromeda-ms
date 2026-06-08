package andromeda.sei.ms;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("flapdoodle embed.mongo 4.20.0 is incompatible with Spring Boot 4 — MongoProperties removed from autoconfigure")
class MsApplicationTests {

	@Test
	void contextLoads() {
	}

}
