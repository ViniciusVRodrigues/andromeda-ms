package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrganizationMongoRepository extends MongoRepository<OrganizationDocument, String> {

    Optional<OrganizationDocument> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    /** {@code lastScheduledSyncAt: null} casa tanto o campo nulo quanto ausente. */
    @Query("{ 'active': true, 'syncSettings.scheduledSync': ?0, "
            + "'$or': [ { 'lastScheduledSyncAt': null }, { 'lastScheduledSyncAt': { '$lte': ?1 } } ] }")
    List<OrganizationDocument> findAllDueForScheduledSync(ScheduledSyncFrequency frequency, Instant dueUpTo);
}
