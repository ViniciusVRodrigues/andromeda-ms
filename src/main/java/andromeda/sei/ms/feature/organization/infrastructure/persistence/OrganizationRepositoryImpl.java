package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrganizationRepositoryImpl implements OrganizationRepository {

    private final OrganizationMongoRepository mongoRepository;
    private final OrganizationDocumentMapper mapper;

    @Override
    public Optional<Organization> findById(String id) {
        return mongoRepository.findById(id)
                .filter(OrganizationDocument::isActive)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Organization> findBySlug(String slug) {
        return mongoRepository.findBySlugAndActiveTrue(slug)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return mongoRepository.existsBySlug(slug);
    }

    @Override
    public Organization save(Organization organization) {
        var doc = mapper.toDocument(organization);
        doc.setUpdatedAt(Instant.now());
        return mapper.toDomain(mongoRepository.save(doc));
    }

    @Override
    public List<Organization> findAllDueForScheduledSync(Instant tick) {
        return Arrays.stream(ScheduledSyncFrequency.values())
                .flatMap(frequency -> frequency.interval().stream()
                        .flatMap(interval -> mongoRepository
                                .findAllDueForScheduledSync(frequency, tick.minus(interval)).stream()))
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Organization> findDeletedById(String id) {
        return mongoRepository.findById(id)
                .filter(doc -> doc.getDeletedAt() != null)
                .map(mapper::toDomain);
    }

    @Override
    public List<Organization> findPendingPurge(Instant deletedBefore) {
        return mongoRepository.findAllByDeletedAtLessThanEqualAndPurgedAtIsNull(deletedBefore).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void markPurged(String id, Instant purgedAt) {
        mongoRepository.findById(id).filter(doc -> doc.getDeletedAt() != null).ifPresent(doc -> {
            doc.setActive(false);
            doc.setSector(null);
            doc.setCountry(null);
            doc.setSeniorities(null);
            doc.setAiPrices(null);
            doc.setSubscriptionCostPerUserUsd(null);
            doc.setSyncSettings(null);
            doc.setLastScheduledSyncAt(null);
            doc.setMetricSettings(null);
            doc.setVcsSettings(null);
            doc.setPurgedAt(purgedAt);
            doc.setUpdatedAt(purgedAt);
            mongoRepository.save(doc);
        });
    }
}
