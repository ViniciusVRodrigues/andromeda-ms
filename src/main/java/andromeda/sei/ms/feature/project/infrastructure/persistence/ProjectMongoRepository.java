package andromeda.sei.ms.feature.project.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProjectMongoRepository extends MongoRepository<ProjectDocument, String> {

    boolean existsByOrganizationIdAndSlug(String organizationId, String slug);
}
