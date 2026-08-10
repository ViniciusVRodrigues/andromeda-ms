package andromeda.sei.ms.feature.github.infrastructure.persistence;

import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GithubRepoBindingRepositoryImpl implements GithubRepoBindingRepository {

    private final GithubRepoBindingMongoRepository mongoRepository;
    private final GithubRepoBindingDocumentMapper mapper;

    @Override
    public Optional<GithubRepoBinding> findByOrganizationIdAndProjectIdAndRepoIdAndActiveTrue(
            String organizationId, String projectId, String repoId) {
        return mongoRepository.findByOrganizationIdAndProjectIdAndRepoIdAndActiveTrue(
                organizationId, projectId, repoId).map(mapper::toDomain);
    }

    @Override
    public Optional<GithubRepoBinding> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId).map(mapper::toDomain);
    }

    @Override
    public List<GithubRepoBinding> findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(String organizationId) {
        return mongoRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(organizationId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<GithubRepoBinding> findAllByOrganizationIdAndActiveTrue(String organizationId) {
        return mongoRepository.findAllByOrganizationIdAndActiveTrue(organizationId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<GithubRepoBinding> findAllByOrganizationIdAndProjectIdAndActiveTrue(
            String organizationId, String projectId) {
        return mongoRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(organizationId, projectId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public GithubRepoBinding save(GithubRepoBinding binding) {
        GithubRepoBindingDocument doc = mapper.toDocument(binding);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }
        return mapper.toDomain(mongoRepository.save(doc));
    }
}
