package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureOrgUser;
import andromeda.sei.ms.feature.azure.domain.AzureOrgUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AzureOrgUserRepositoryImpl implements AzureOrgUserRepository {

    private final AzureOrgUserMongoRepository mongoRepository;
    private final AzureOrgUserDocumentMapper mapper;

    @Override
    public List<AzureOrgUser> findAllByOrganizationIdAndActiveTrue(String organizationId) {
        return mongoRepository.findAllByOrganizationIdAndActiveTrue(organizationId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<AzureOrgUser> findByOrganizationIdAndAzureIdAndActiveTrue(
            String organizationId, String azureId) {
        return mongoRepository.findByOrganizationIdAndAzureIdAndActiveTrue(organizationId, azureId)
                .map(mapper::toDomain);
    }

    @Override
    public AzureOrgUser save(AzureOrgUser user) {
        AzureOrgUserDocument doc = mapper.toDocument(user);
        doc.setUpdatedAt(Instant.now());
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(doc.getUpdatedAt());
        }
        return mapper.toDomain(mongoRepository.save(doc));
    }

    @Override
    public void saveAll(List<AzureOrgUser> users) {
        Instant now = Instant.now();
        List<AzureOrgUserDocument> docs = users.stream().map(u -> {
            String existingId = u.getId() != null ? u.getId() : mongoRepository
                    .findByOrganizationIdAndAzureIdAndActiveTrue(u.getOrganizationId(), u.getAzureId())
                    .map(AzureOrgUserDocument::getId)
                    .orElse(null);
            AzureOrgUserDocument doc = mapper.toDocument(u);
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
