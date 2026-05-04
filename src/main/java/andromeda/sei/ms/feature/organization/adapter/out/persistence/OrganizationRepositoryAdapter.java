package andromeda.sei.ms.feature.organization.adapter.out.persistence;

import andromeda.sei.ms.feature.organization.application.port.out.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrganizationRepositoryAdapter implements OrganizationRepository {

    private final OrganizationJpaRepository jpaRepository;

    @Override
    public Organization save(Organization organization) {
        OrganizationJpaEntity entity = OrganizationJpaEntity.builder()
                .name(organization.name())
                .slug(organization.slug())
                .build();
        OrganizationJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpaRepository.existsBySlug(slug);
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private Organization toDomain(OrganizationJpaEntity entity) {
        return new Organization(entity.getId(), entity.getName(), entity.getSlug(), entity.getCreatedAt());
    }
}
