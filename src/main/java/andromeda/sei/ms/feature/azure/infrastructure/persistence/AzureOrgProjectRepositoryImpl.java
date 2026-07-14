package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureOrgProject;
import andromeda.sei.ms.feature.azure.domain.AzureOrgProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AzureOrgProjectRepositoryImpl implements AzureOrgProjectRepository {

    private final AzureOrgProjectMongoRepository mongoRepository;
    private final AzureOrgProjectDocumentMapper mapper;

    @Override
    public List<AzureOrgProject> findAllByOrganizationIdAndActiveTrue(String organizationId) {
        return mongoRepository.findAllByOrganizationIdAndActiveTrue(organizationId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<AzureOrgProject> findByOrganizationIdAndAzureProjectIdAndActiveTrue(
            String organizationId, String azureProjectId) {
        return mongoRepository
                .findByOrganizationIdAndAzureProjectIdAndActiveTrue(organizationId, azureProjectId)
                .map(mapper::toDomain);
    }

    @Override
    public AzureOrgProject save(AzureOrgProject project) {
        AzureOrgProjectDocument doc = mapper.toDocument(project);
        doc.setUpdatedAt(Instant.now());
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(doc.getUpdatedAt());
        }
        return mapper.toDomain(mongoRepository.save(doc));
    }

    @Override
    public void saveAll(List<AzureOrgProject> projects) {
        Instant now = Instant.now();
        List<AzureOrgProjectDocument> docs = projects.stream().map(p -> {
            String existingId = p.getId() != null ? p.getId() : mongoRepository
                    .findByOrganizationIdAndAzureProjectIdAndActiveTrue(p.getOrganizationId(), p.getAzureProjectId())
                    .map(AzureOrgProjectDocument::getId)
                    .orElse(null);
            AzureOrgProjectDocument doc = mapper.toDocument(p);
            doc.setId(existingId);
            doc.setUpdatedAt(now);
            if (doc.getCreatedAt() == null) {
                doc.setCreatedAt(now);
            }
            return doc;
        }).toList();
        mongoRepository.saveAll(docs);
    }
}
