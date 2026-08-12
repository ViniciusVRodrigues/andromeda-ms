package andromeda.sei.ms.feature.github.infrastructure.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GithubRepoBindingMongoRepository extends MongoRepository<GithubRepoBindingDocument, String> {

    Optional<GithubRepoBindingDocument> findByOrganizationIdAndProjectIdAndRepoIdAndActiveTrue(
            String organizationId, String projectId, String repoId);

    Optional<GithubRepoBindingDocument> findByOrganizationIdAndRepoIdAndActiveTrue(
            String organizationId, String repoId);

    Optional<GithubRepoBindingDocument> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId);

    List<GithubRepoBindingDocument> findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(String organizationId);

    List<GithubRepoBindingDocument> findAllByOrganizationIdAndActiveTrue(String organizationId);

    List<GithubRepoBindingDocument> findAllByOrganizationIdAndProjectIdAndActiveTrue(
            String organizationId, String projectId);
}
