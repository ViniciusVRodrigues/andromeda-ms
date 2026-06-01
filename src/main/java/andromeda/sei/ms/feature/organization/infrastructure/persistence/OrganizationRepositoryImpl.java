package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
}
