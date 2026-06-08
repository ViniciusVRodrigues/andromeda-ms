package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AzureProjectBindingRepositoryImpl implements AzureProjectBindingRepository {

    private final AzureProjectBindingMongoRepository mongoRepository;
    private final AzureProjectBindingDocumentMapper mapper;

    @Override
    public Optional<AzureProjectBinding> findByOrganizationIdAndProjectIdAndActiveTrue(
            String organizationId, String projectId) {
        return mongoRepository.findByOrganizationIdAndProjectIdAndActiveTrue(organizationId, projectId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<AzureProjectBinding> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId)
                .map(mapper::toDomain);
    }

    @Override
    public List<AzureProjectBinding> findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(String organizationId) {
        return mongoRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(organizationId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AzureProjectBinding> findAllByOrganizationIdAndActiveTrue(String organizationId) {
        return mongoRepository.findAllByOrganizationIdAndActiveTrue(organizationId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public AzureProjectBinding save(AzureProjectBinding binding) {
        AzureProjectBindingDocument doc = mapper.toDocument(binding);
        doc.setUpdatedAt(Instant.now());
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(doc.getUpdatedAt());
        }
        return mapper.toDomain(mongoRepository.save(doc));
    }

    @Override
    public void saveAll(List<AzureProjectBinding> bindings) {
        Instant now = Instant.now();
        List<AzureProjectBindingDocument> docs = bindings.stream().map(b -> {
            AzureProjectBindingDocument doc = mapper.toDocument(b);
            doc.setUpdatedAt(now);
            return doc;
        }).toList();
        mongoRepository.saveAll(docs);
    }
}
