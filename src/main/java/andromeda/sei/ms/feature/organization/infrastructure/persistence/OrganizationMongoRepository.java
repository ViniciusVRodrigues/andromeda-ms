package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrganizationMongoRepository extends MongoRepository<OrganizationDocument, String> {

    Optional<OrganizationDocument> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);
}
