package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureIntegrationDataWiper;
import andromeda.sei.ms.feature.connector.infrastructure.persistence.AiConnectorIntegrationDataWiper;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import andromeda.sei.ms.feature.organization.application.usecase.PurgeOrganizationDataUseCase;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsIntegrationDataWiper;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Duas organizacoes, so uma some": o purge real contra o Mongo real, em cada colecao da
 * organizacao. A outra organizacao, os {@code roles} globais e as {@code permissions} ficam, e a
 * organizacao excluida vira lapide.
 */
@MongoIntegrationTest
class PurgeOrganizationDataIntegrationTest {

    private static final String GONE = "org-gone";
    private static final String KEPT = "org-kept";
    private static final Instant DELETED_AT = Instant.parse("2026-10-26T10:00:00Z");

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private OrganizationMongoRepository mongoRepository;

    private OrganizationRepositoryImpl organizations;
    private MongoOrganizationDataPurger purger;
    private List<IntegrationDataWiper> wipers;
    private PurgeOrganizationDataUseCase useCase;

    @BeforeEach
    void setUp() {
        organizations = new OrganizationRepositoryImpl(mongoRepository, new OrganizationDocumentMapper());
        purger = new MongoOrganizationDataPurger(mongoTemplate);
        wipers = List.of(
                new AzureIntegrationDataWiper(mongoTemplate),
                new AiConnectorIntegrationDataWiper(mongoTemplate),
                new VcsIntegrationDataWiper(mongoTemplate, VcsProvider.GITHUB),
                new VcsIntegrationDataWiper(mongoTemplate, VcsProvider.AZURE_REPOS));
        useCase = new PurgeOrganizationDataUseCase(wipers, purger, organizations);

        mongoRepository.deleteAll();
        for (String collection : allCollections()) {
            mongoTemplate.getCollection(collection).deleteMany(new Document());
        }
        mongoTemplate.getCollection("permissions").deleteMany(new Document());
    }

    @Test
    void should_removeOnlyTheDeletedOrganization_inEveryCollection() {
        seedOrganization(GONE, true);
        seedOrganization(KEPT, false);
        for (String collection : allCollections()) {
            insert(collection, GONE);
            insert(collection, KEPT);
        }

        useCase.execute(GONE);

        for (String collection : allCollections()) {
            assertThat(count(collection, GONE)).as("%s da org excluida", collection).isZero();
            assertThat(count(collection, KEPT)).as("%s da outra org", collection).isEqualTo(1);
        }
    }

    @Test
    void should_keepGlobalRolesAndPermissions() {
        seedOrganization(GONE, true);
        mongoTemplate.getCollection("roles").insertOne(new Document("name", "ADMIN").append("organizationId", null));
        mongoTemplate.getCollection("roles").insertOne(new Document("name", "CUSTOM").append("organizationId", GONE));
        mongoTemplate.getCollection("permissions").insertOne(new Document("code", "USER_MANAGE"));

        useCase.execute(GONE);

        assertThat(mongoTemplate.getCollection("roles").countDocuments(new Document("name", "ADMIN"))).isEqualTo(1);
        assertThat(mongoTemplate.getCollection("roles").countDocuments(new Document("name", "CUSTOM"))).isZero();
        assertThat(mongoTemplate.getCollection("permissions").countDocuments()).isEqualTo(1);
    }

    @Test
    void should_reduceTheOrganizationToATombstone() {
        seedOrganization(GONE, true);
        seedOrganization(KEPT, false);

        useCase.execute(GONE);

        OrganizationDocument tombstone = mongoRepository.findById(GONE).orElseThrow();
        assertThat(tombstone.getName()).isEqualTo("Acme");
        assertThat(tombstone.getSlug()).isEqualTo("slug-org-gone");
        assertThat(tombstone.isActive()).isFalse();
        assertThat(tombstone.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(tombstone.getDeletedBy()).isEqualTo("user-1");
        assertThat(tombstone.getPurgedAt()).isNotNull();
        assertThat(tombstone.getCreatedAt()).isNotNull();
        assertThat(tombstone.getUpdatedAt()).isNotNull();
        assertThat(tombstone.getVersion()).isNotNull();
        assertThat(tombstone.getSector()).isNull();
        assertThat(tombstone.getCountry()).isNull();
        assertThat(tombstone.getSeniorities()).isNull();
        assertThat(tombstone.getAiPrices()).isNull();
        assertThat(tombstone.getSubscriptionCostPerUserUsd()).isNull();
        assertThat(tombstone.getSyncSettings()).isNull();
        assertThat(tombstone.getLastScheduledSyncAt()).isNull();
        assertThat(tombstone.getMetricSettings()).isNull();
        assertThat(tombstone.getVcsSettings()).isNull();
        assertThat(tombstone.getAgentSettings()).isNull();

        OrganizationDocument other = mongoRepository.findById(KEPT).orElseThrow();
        assertThat(other.getPurgedAt()).isNull();
        assertThat(other.getSector()).isEqualTo("tech");
        assertThat(other.isActive()).isTrue();
    }

    @Test
    void should_keepTheSlugReserved_afterThePurge() {
        seedOrganization(GONE, true);

        useCase.execute(GONE);

        assertThat(organizations.existsBySlug("slug-org-gone")).isTrue();
        assertThat(organizations.findById(GONE)).isEmpty();
    }

    @Test
    void should_beIdempotent_whenRunTwice() {
        seedOrganization(GONE, true);
        insert("users", GONE);

        useCase.execute(GONE);
        Instant firstPurgedAt = mongoRepository.findById(GONE).orElseThrow().getPurgedAt();
        useCase.execute(GONE);

        assertThat(mongoRepository.findById(GONE).orElseThrow().getPurgedAt()).isEqualTo(firstPurgedAt);
        assertThat(count("users", GONE)).isZero();
    }

    @Test
    void should_notTouchALiveOrganization() {
        seedOrganization(KEPT, false);
        insert("users", KEPT);

        useCase.execute(KEPT);

        assertThat(count("users", KEPT)).isEqualTo(1);
        assertThat(mongoRepository.findById(KEPT).orElseThrow().getPurgedAt()).isNull();
    }

    @Test
    void should_leavePurgedAtEmptyAndResume_whenAWiperFails() {
        seedOrganization(GONE, true);
        insert("users", GONE);
        List<IntegrationDataWiper> failing = new ArrayList<>(wipers);
        failing.add(0, new FailingWiper());
        var failingUseCase = new PurgeOrganizationDataUseCase(failing, purger, organizations);

        failingUseCase.execute(GONE);

        assertThat(mongoRepository.findById(GONE).orElseThrow().getPurgedAt()).isNull();
        assertThat(count("users", GONE)).isEqualTo(1);
        assertThat(organizations.findPendingPurge(DELETED_AT.plus(1, ChronoUnit.DAYS))).extracting("id").containsExactly(GONE);

        useCase.execute(GONE);

        assertThat(mongoRepository.findById(GONE).orElseThrow().getPurgedAt()).isNotNull();
        assertThat(count("users", GONE)).isZero();
    }

    @Test
    void should_listOnlyPendingTombstones_untilTheBound() {
        seedOrganization(GONE, true);
        seedOrganization(KEPT, false);
        seedOrganization("org-done", true);
        organizations.markPurged("org-done", Instant.now());

        assertThat(organizations.findPendingPurge(DELETED_AT.plus(1, ChronoUnit.MINUTES)))
                .extracting("id").containsExactly(GONE);
        assertThat(organizations.findPendingPurge(DELETED_AT.minus(1, ChronoUnit.MINUTES))).isEmpty();
        assertThat(organizations.findDeletedById(GONE)).isPresent();
        assertThat(organizations.findDeletedById(KEPT)).isEmpty();
    }

    @Test
    void should_refuseABlankOrganizationId() {
        assertThatThrownBy(() -> purger.purge(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> purger.purge(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private Set<String> allCollections() {
        Set<String> all = new TreeSet<>(purger.collections());
        wipers.forEach(w -> all.addAll(w.collections()));
        return all;
    }

    private void seedOrganization(String id, boolean deleted) {
        mongoRepository.save(OrganizationDocument.builder()
                .id(id)
                .name("Acme")
                .slug("slug-" + id)
                .sector("tech")
                .country("BR")
                .seniorities(List.of("JUNIOR"))
                .active(!deleted)
                .deletedAt(deleted ? DELETED_AT : null)
                .deletedBy(deleted ? "user-1" : null)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .syncSettings(new SyncSettingsDocument(ScheduledSyncFrequency.DAILY, 90))
                .agentSettings(new AgentSettingsDocument(List.of("wi-([0-9]+)")))
                .build());
    }

    private void insert(String collection, String organizationId) {
        Document document = new Document("organizationId", organizationId);
        // indices unicos de outros campos (ex.: ticketHash) rejeitam varios documentos com o campo nulo
        for (Document index : mongoTemplate.getCollection(collection).listIndexes()) {
            if (Boolean.TRUE.equals(index.getBoolean("unique"))) {
                index.get("key", Document.class).keySet().stream()
                        .filter(key -> !key.equals("organizationId") && !key.equals("_id"))
                        .forEach(key -> document.put(key, UUID.randomUUID().toString()));
            }
        }
        mongoTemplate.getCollection(collection).insertOne(document);
    }

    private long count(String collection, String organizationId) {
        return mongoTemplate.getCollection(collection).countDocuments(new Document("organizationId", organizationId));
    }

    private static class FailingWiper implements IntegrationDataWiper {
        @Override public Integration integration() { return Integration.AI_CONNECTOR; }
        @Override public Set<String> collections() { return Set.of(); }
        @Override public Map<String, Long> wipe(String organizationId, WipeScope scope) {
            throw new IllegalStateException("boom");
        }
    }
}
