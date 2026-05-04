package andromeda.sei.ms.feature.user.adapter.out.persistence;

import andromeda.sei.ms.feature.user.application.port.out.UserRepository;
import andromeda.sei.ms.feature.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    @Override
    public User save(User user) {
        UserJpaEntity entity;
        if (user.id() != null) {
            entity = jpaRepository.findById(user.id()).orElse(new UserJpaEntity());
            entity.setId(user.id());
        } else {
            entity = new UserJpaEntity();
        }
        entity.setOrganizationId(user.organizationId());
        entity.setEmail(user.email());
        entity.setPasswordHash(user.passwordHash());
        entity.setName(user.name());
        entity.setRoleId(user.roleId());
        entity.setGithubId(user.githubId());
        entity.setActive(user.active());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return jpaRepository.findByIdAndOrganizationId(id, organizationId).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmailAndOrganizationId(String email, UUID organizationId) {
        return jpaRepository.findByEmailAndOrganizationId(email, organizationId).map(this::toDomain);
    }

    @Override
    public List<User> findAllByOrganizationId(UUID organizationId) {
        return jpaRepository.findAllByOrganizationId(organizationId).stream().map(this::toDomain).toList();
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(entity.getId(), entity.getOrganizationId(), entity.getEmail(), entity.getPasswordHash(),
                entity.getName(), entity.getRoleId(), entity.getGithubId(), entity.isActive(), entity.getCreatedAt());
    }
}
