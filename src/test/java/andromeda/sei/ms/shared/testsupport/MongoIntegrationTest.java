package andromeda.sei.ms.shared.testsupport;

import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Slice de persistencia: {@code @DataMongoTest} contra o Mongo real do Testcontainers. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataMongoTest
@Import(MongoTestcontainersConfig.class)
public @interface MongoIntegrationTest {
}
