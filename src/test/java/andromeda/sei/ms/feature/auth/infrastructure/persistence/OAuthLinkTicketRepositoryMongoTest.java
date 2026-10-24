package andromeda.sei.ms.feature.auth.infrastructure.persistence;

import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class OAuthLinkTicketRepositoryMongoTest {

    private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");

    @Autowired private MongoTemplate mongoTemplate;
    private OAuthLinkTicketRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new OAuthLinkTicketRepositoryImpl(mongoTemplate);
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), "oauth_link_tickets");
    }

    private OAuthLinkTicket ticket(String hash, OAuthLinkPurpose p, String url) {
        return OAuthLinkTicket.issue("org-1", "user-1", hash, p, url, NOW, 60);
    }

    @Test
    void should_consumeOnce_andReturnEmptyOnSecondUse() {
        repository.save(ticket("h1", OAuthLinkPurpose.GITHUB_PROFILE, null));

        var first = repository.consume("h1", OAuthLinkPurpose.GITHUB_PROFILE, NOW.plusSeconds(10));
        var second = repository.consume("h1", OAuthLinkPurpose.GITHUB_PROFILE, NOW.plusSeconds(11));

        assertThat(first).isPresent();
        assertThat(first.get().getUserId()).isEqualTo("user-1");
        assertThat(first.get().getOrganizationId()).isEqualTo("org-1");
        assertThat(second).isEmpty();
    }

    @Test
    void should_returnEmpty_whenExpired() {
        repository.save(ticket("h2", OAuthLinkPurpose.GITHUB_PROFILE, null));

        assertThat(repository.consume("h2", OAuthLinkPurpose.GITHUB_PROFILE, NOW.plusSeconds(61))).isEmpty();
    }

    @Test
    void should_returnEmpty_whenPurposeDiffers_andKeepTicketUsable() {
        repository.save(ticket("h3", OAuthLinkPurpose.GITHUB_PROFILE, null));

        assertThat(repository.consume("h3", OAuthLinkPurpose.AZURE_ORG_CONNECT, NOW.plusSeconds(1))).isEmpty();
        assertThat(repository.consume("h3", OAuthLinkPurpose.GITHUB_PROFILE, NOW.plusSeconds(2))).isPresent();
    }

    @Test
    void should_keepAzureOrgUrl() {
        repository.save(ticket("h4", OAuthLinkPurpose.AZURE_ORG_CONNECT, "https://dev.azure.com/acme"));

        var got = repository.consume("h4", OAuthLinkPurpose.AZURE_ORG_CONNECT, NOW.plusSeconds(1));

        assertThat(got).get().extracting(OAuthLinkTicket::getAzureOrgUrl).isEqualTo("https://dev.azure.com/acme");
    }

    @Test
    void should_returnEmpty_forUnknownHash() {
        assertThat(repository.consume("nope", OAuthLinkPurpose.GITHUB_PROFILE, NOW)).isEmpty();
    }

    @Test
    void should_letOnlyOneConcurrentConsumerWin() throws Exception {
        repository.save(ticket("h5", OAuthLinkPurpose.GITHUB_PROFILE, null));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = java.util.stream.IntStream.range(0, 8)
                    .<Callable<Boolean>>mapToObj(i -> () ->
                            repository.consume("h5", OAuthLinkPurpose.GITHUB_PROFILE, NOW.plusSeconds(1)).isPresent())
                    .toList();
            long winners = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                if (f.get()) winners++;
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void should_declareUniqueHashIndex_andImmediateTtlOnExpiresAt() {
        List<IndexInfo> indexes = mongoTemplate.indexOps("oauth_link_tickets").getIndexInfo();

        assertThat(indexes).anySatisfy(i -> {
            assertThat(i.getIndexFields().get(0).getKey()).isEqualTo("expiresAt");
            assertThat(i.getExpireAfter()).contains(Duration.ZERO);
        });
        assertThat(indexes).anySatisfy(i -> {
            assertThat(i.getIndexFields().get(0).getKey()).isEqualTo("ticketHash");
            assertThat(i.isUnique()).isTrue();
        });
    }
}
