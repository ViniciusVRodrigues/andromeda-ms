package andromeda.sei.ms.feature.auth.adapter.out.persistence;

import andromeda.sei.ms.feature.auth.application.port.out.UserAuthRepository;
import andromeda.sei.ms.feature.user.adapter.out.persistence.UserJpaRepository;
import andromeda.sei.ms.feature.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserAuthRepositoryAdapter implements UserAuthRepository {

    private final UserJpaRepository jpaRepository;

    @Override
    public Optional<User> findByEmail(String email) {
        // search across all organizations - for login we find by email
        return jpaRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .map(e -> new User(e.getId(), e.getOrganizationId(), e.getEmail(), e.getPasswordHash(),
                        e.getName(), e.getRoleId(), e.getGithubId(), e.isActive(), e.getCreatedAt()));
    }

    @Override
    @Transactional
    public void updateGithubId(UUID userId, String githubId) {
        jpaRepository.findById(userId).ifPresent(e -> {
            e.setGithubId(githubId);
            jpaRepository.save(e);
        });
    }
}
