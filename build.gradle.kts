plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "andromeda.sei"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(26)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("com.github.ben-manes.caffeine:caffeine")  // cache de permissões efetivas (project-scoped-permissions D5)
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("com.sendinblue:sib-api-v3-sdk:7.0.0")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")  // JUnit 5, Mockito, @DataMongoTest
	testImplementation("org.springframework.security:spring-security-test")
	// Mongo real nos testes de persistencia: container mongo:8.0 via Testcontainers (versoes geridas pelo BOM do Boot)
	testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-test")  // @DataMongoTest (Boot 4)
	testImplementation("org.springframework.boot:spring-boot-testcontainers")  // @ServiceConnection
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mongodb")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// Mensagem e causas completas no log do CI (o formato curto esconde o erro do Testcontainers).
	testLogging {
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		showStandardStreams = System.getenv("CI") != null && System.getenv("TC_DIAG") != null
	}
}

// Carrega .env como variáveis de ambiente no bootRun (nunca commitar .env)
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	val envFile = file(".env")
	if (envFile.exists()) {
		envFile.readLines()
			.filter { it.isNotBlank() && !it.startsWith("#") && "=" in it }
			.forEach { line ->
				val idx = line.indexOf("=")
				val key = line.substring(0, idx).trim()
				val value = line.substring(idx + 1).trim()
				environment(key, value)
			}
	}
}
