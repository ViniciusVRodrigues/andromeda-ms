package andromeda.sei.ms.feature.role.infrastructure.persistence;

import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleMongoRepository mongoRepository;
    private final RoleDocumentMapper mapper;

    @Override
    public Optional<Role> findById(String id) {
        return mongoRepository.findById(id)
                .filter(RoleDocument::isActive)
                .map(mapper::toDomain);
    }

    @Override
    public List<Role> findAvailableForOrganization(String organizationId) {
        List<RoleDocument> defaults = mongoRepository.findByOrganizationIdIsNullAndActiveTrue();
        List<RoleDocument> orgSpecific = mongoRepository.findByOrganizationIdAndActiveTrue(organizationId);

        List<Role> result = new ArrayList<>();
        defaults.forEach(doc -> result.add(mapper.toDomain(doc)));
        orgSpecific.forEach(doc -> result.add(mapper.toDomain(doc)));
        return result;
    }

    @Override
    public List<Role> findAllGlobal() {
        return mongoRepository.findByOrganizationIdIsNullAndActiveTrue().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Role> findGlobalByName(String name) {
        return mongoRepository.findByNameAndOrganizationIdIsNull(name)
                .filter(RoleDocument::isActive)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Role> findByNameAndOrganizationId(String name, String organizationId) {
        return mongoRepository.findByNameAndOrganizationId(name, organizationId)
                .filter(RoleDocument::isActive)
                .map(mapper::toDomain);
    }

    @Override
    public Role save(Role role) {
        RoleDocument doc = mapper.toDocument(role);
        doc.setUpdatedAt(Instant.now());
        RoleDocument saved = mongoRepository.save(doc);
        return mapper.toDomain(saved);
    }
}
