package andromeda.sei.ms.feature.user.infrastructure.persistence;

import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMongoRepository mongoRepository;
    private final UserDocumentMapper mapper;

    @Override
    public Optional<User> findByIdAndOrganizationId(String id, String organizationId) {
        return mongoRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId)
                .map(mapper::toDomain);
    }

    @Override
    public List<User> findAllByOrganizationId(String organizationId) {
        return mongoRepository.findByOrganizationIdAndActiveTrue(organizationId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByEmail(String email) {
        return mongoRepository.existsByEmailAndActiveTrue(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return mongoRepository.findByEmailAndActiveTrue(email).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByOrganizationIdAndAzureId(String organizationId, String azureId) {
        return mongoRepository.findByOrganizationIdAndAzureIdAndActiveTrue(organizationId, azureId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByOrganizationIdAndGithubId(String organizationId, String githubId) {
        return mongoRepository.findByOrganizationIdAndGithubIdAndActiveTrue(organizationId, githubId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByInviteToken(String inviteToken) {
        return mongoRepository.findByInviteToken(inviteToken).map(mapper::toDomain);
    }

    @Override
    public boolean existsByOrganizationIdAndRoleIdAndActiveTrue(String organizationId, String roleId) {
        return mongoRepository.existsByOrganizationIdAndRoleIdAndActiveTrue(organizationId, roleId);
    }

    @Override
    public User save(User user) {
        UserDocument doc = mapper.toDocument(user);
        doc.setUpdatedAt(Instant.now());
        UserDocument saved = mongoRepository.save(doc);
        return mapper.toDomain(saved);
    }
}
