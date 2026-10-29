package andromeda.sei.ms.feature.agent.infrastructure.persistence;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MongoIntegrationTest
class AgentRepositoriesMongoTest {

    private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AgentDeviceMongoRepository deviceMongo;
    @Autowired private AgentDeviceAuthorizationMongoRepository authorizationMongo;
    private AgentDeviceRepositoryImpl devices;
    private AgentDeviceAuthorizationRepositoryImpl authorizations;

    @BeforeEach
    void setUp() {
        AgentDocumentMapper mapper = new AgentDocumentMapper();
        devices = new AgentDeviceRepositoryImpl(deviceMongo, mongoTemplate, mapper);
        authorizations = new AgentDeviceAuthorizationRepositoryImpl(authorizationMongo, mapper);
        mongoTemplate.remove(new Query(), "agent_devices");
        mongoTemplate.remove(new Query(), "agent_device_authorizations");
    }

    private AgentDevice device(String access, String refresh) {
        AgentDevice d = new AgentDevice();
        d.setOrganizationId("org-1");
        d.setUserId("user-1");
        d.setMachineName("NOTE");
        d.setOs("windows");
        d.setTimezone("UTC");
        d.setAgentVersion("0.2.0");
        d.setAccessTokenHash(access);
        d.setRefreshTokenHash(refresh);
        d.setPairedAt(NOW);
        d.setActive(true);
        d.setCreatedAt(NOW);
        d.setUpdatedAt(NOW);
        return d;
    }

    private AgentDeviceAuthorization authorization(String deviceHash, String userHash) {
        AgentDeviceAuthorization a = new AgentDeviceAuthorization();
        a.setDeviceCodeHash(deviceHash);
        a.setUserCodeHash(userHash);
        a.setStatus(AgentDeviceAuthorization.Status.PENDING);
        a.setMachineName("NOTE");
        a.setOs("linux");
        a.setTimezone("UTC");
        a.setAgentVersion("0.2.0");
        a.setIntervalSeconds(5);
        a.setExpiresAt(NOW.plusSeconds(600));
        a.setPurgeAt(NOW.plus(Duration.ofDays(1)));
        a.setActive(true);
        a.setCreatedAt(NOW);
        a.setUpdatedAt(NOW);
        return a;
    }

    @Test
    void should_rejectDuplicateAccessAndRefreshHashes() {
        devices.save(device("a1", "r1"));
        assertThatThrownBy(() -> devices.save(device("a1", "r2"))).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> devices.save(device("a2", "r1"))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void should_allowManyDevicesWithoutHashesBecauseTheUniqueIndexIsPartial() {
        AgentDevice a = devices.save(device("a1", "r1"));
        a.revoke("user-1", NOW);
        devices.save(a);
        AgentDevice b = devices.save(device("a2", "r2"));
        b.revoke("user-1", NOW);
        devices.save(b);
        devices.save(device("a3", "r3")).revoke("user-1", NOW);
        assertThat(devices.findDevicesOfUser("org-1", "user-1")).hasSize(3);
    }

    @Test
    void should_rejectDuplicateDeviceAndUserCodeHashes() {
        authorizations.save(authorization("d1", "u1"));
        assertThatThrownBy(() -> authorizations.save(authorization("d1", "u2"))).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> authorizations.save(authorization("d2", "u1"))).isInstanceOf(DuplicateKeyException.class);
        assertThat(authorizations.findByDeviceCodeHash("d1")).isPresent();
        assertThat(authorizations.findByUserCodeHash("u1")).isPresent();
    }

    @Test
    void should_declareTheUniqueIndexesAndTheImmediateTtlOnPurgeAt() {
        List<IndexInfo> deviceIndexes = mongoTemplate.indexOps("agent_devices").getIndexInfo();
        assertThat(deviceIndexes).anySatisfy(i -> {
            assertThat(i.getIndexFields().get(0).getKey()).isEqualTo("accessTokenHash");
            assertThat(i.isUnique()).isTrue();
            assertThat(i.getPartialFilterExpression()).isNotNull();
        });
        assertThat(deviceIndexes).anySatisfy(i -> {
            assertThat(i.getIndexFields().get(0).getKey()).isEqualTo("refreshTokenHash");
            assertThat(i.isUnique()).isTrue();
            assertThat(i.getPartialFilterExpression()).isNotNull();
        });
        List<IndexInfo> authIndexes = mongoTemplate.indexOps("agent_device_authorizations").getIndexInfo();
        assertThat(authIndexes).anySatisfy(i -> {
            assertThat(i.getIndexFields().get(0).getKey()).isEqualTo("deviceCodeHash");
            assertThat(i.isUnique()).isTrue();
        });
        assertThat(authIndexes).anySatisfy(i -> {
            assertThat(i.getIndexFields().get(0).getKey()).isEqualTo("userCodeHash");
            assertThat(i.isUnique()).isTrue();
        });
        assertThat(authIndexes).anySatisfy(i -> {
            assertThat(i.getIndexFields().get(0).getKey()).isEqualTo("purgeAt");
            assertThat(i.getExpireAfter()).contains(Duration.ZERO);
        });
    }

    @Test
    void should_neverStoreTheRawCodes() {
        authorizations.save(authorization("hash-d", "hash-u"));
        String raw = mongoTemplate.findAll(org.bson.Document.class, "agent_device_authorizations").toString();
        assertThat(raw).contains("hash-d").doesNotContain("adc_").doesNotContain("deviceCode=");
    }

    @Test
    void should_writeSeenHeartbeatAndUploadWithoutBumpingTheVersion() {
        AgentDevice saved = devices.save(device("a1", "r1"));
        Long version = devices.findByIdAndOrganizationId(saved.getId(), "org-1").orElseThrow().getVersion();
        devices.touchSeen(saved.getId(), "org-1", NOW.plusSeconds(5), "0.2.1");
        devices.recordHeartbeat(saved.getId(), "org-1", NOW.plusSeconds(10), "0.2.1", 4, "Europe/Lisbon");
        AgentDevice afterHeartbeat = devices.findByIdAndOrganizationId(saved.getId(), "org-1").orElseThrow();
        assertThat(afterHeartbeat.getLastSeenAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(afterHeartbeat.getTimezone()).isEqualTo("Europe/Lisbon");
        assertThat(afterHeartbeat.getPendingRows()).isEqualTo(4);
        assertThat(afterHeartbeat.getLastUploadAt()).isNull();
        assertThat(afterHeartbeat.getVersion()).isEqualTo(version);
        devices.touchUpload(saved.getId(), "org-1", NOW.plusSeconds(20));
        assertThat(devices.findByIdAndOrganizationId(saved.getId(), "org-1").orElseThrow().getLastUploadAt())
                .isEqualTo(NOW.plusSeconds(20));
        assertThat(devices.findByIdAndOrganizationId(saved.getId(), "org-2")).isEmpty();
    }

    @Test
    void should_requestSyncOnlyWhenNonePendingAndOnlyInTheOwnOrganization() {
        AgentDevice d = devices.save(device("a1", "r1"));
        Instant t1 = NOW.plusSeconds(10);
        assertThat(devices.requestSync(d.getId(), "org-2", t1, "user-1")).isFalse();
        assertThat(devices.findByIdAndOrganizationId(d.getId(), "org-1").orElseThrow().getSyncRequestedAt()).isNull();
        assertThat(devices.requestSync(d.getId(), "org-1", t1, "user-1")).isTrue();
        assertThat(devices.requestSync(d.getId(), "org-1", t1.plusSeconds(5), "user-2")).isFalse();
        AgentDevice stored = devices.findByIdAndOrganizationId(d.getId(), "org-1").orElseThrow();
        assertThat(stored.getSyncRequestedAt()).isEqualTo(t1);
        assertThat(stored.getSyncRequestedBy()).isEqualTo("user-1");
        assertThat(stored.getVersion()).isEqualTo(d.getVersion());
    }

    @Test
    void should_notRequestSyncOnARevokedDevice() {
        AgentDevice d = devices.save(device("a1", "r1"));
        d.revoke("user-1", NOW);
        devices.save(d);
        assertThat(devices.requestSync(d.getId(), "org-1", NOW.plusSeconds(1), "user-1")).isFalse();
    }

    @Test
    void should_clearServedSyncOnlyWhenTheRequestMatchesAndInTheOwnOrganization() {
        AgentDevice d = devices.save(device("a1", "r1"));
        Instant t1 = NOW.plusSeconds(10);
        devices.requestSync(d.getId(), "org-1", t1, "user-1");

        devices.clearServedSync(d.getId(), "org-2", t1, NOW.plusSeconds(20));
        assertThat(devices.findByIdAndOrganizationId(d.getId(), "org-1").orElseThrow().getSyncRequestedAt())
                .isEqualTo(t1);

        devices.clearServedSync(d.getId(), "org-1", t1.minusSeconds(1), NOW.plusSeconds(20));
        AgentDevice kept = devices.findByIdAndOrganizationId(d.getId(), "org-1").orElseThrow();
        assertThat(kept.getSyncRequestedAt()).isEqualTo(t1);
        assertThat(kept.getSyncServedAt()).isNull();

        devices.clearServedSync(d.getId(), "org-1", t1, NOW.plusSeconds(20));
        AgentDevice cleared = devices.findByIdAndOrganizationId(d.getId(), "org-1").orElseThrow();
        assertThat(cleared.getSyncRequestedAt()).isNull();
        assertThat(cleared.getSyncRequestedBy()).isNull();
        assertThat(cleared.getSyncServedAt()).isEqualTo(NOW.plusSeconds(20));
        assertThat(devices.requestSync(d.getId(), "org-1", NOW.plusSeconds(30), "user-1")).isTrue();
    }
}
