package andromeda.sei.ms.feature.user.infrastructure.persistence;

import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMongoRepository mongoRepository;
    private final UserDocumentMapper mapper;
    private final MongoTemplate mongoTemplate;

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
    public List<User> findAllByOrganizationIdIncludingInactive(String organizationId) {
        return mongoRepository.findByOrganizationId(organizationId).stream().map(mapper::toDomain).toList();
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
    public List<User> findAllByOrganizationIdAndEmailIgnoringCase(String organizationId, String email) {
        return mongoRepository.findAllByOrganizationIdAndEmailIgnoreCaseAndActiveTrue(organizationId, email)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<User> findAllByOrganizationIdAndAzureEmailIgnoringCase(String organizationId, String azureEmail) {
        return mongoRepository.findAllByOrganizationIdAndAzureEmailIgnoreCaseAndActiveTrue(organizationId, azureEmail)
                .stream().map(mapper::toDomain).toList();
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
        try {
            return mapper.toDomain(mongoRepository.save(doc));
        } catch (OptimisticLockingFailureException e) {
            // Concurrent write on the same version (e.g. two admins replacing overrides).
            throw new UserConflictException(user.getId());
        }
    }

    @Override
    public void recordLogin(String userId, String organizationId, Instant at) {
        Query query = Query.query(Criteria.where("_id").is(userId)
                .and("organizationId").is(organizationId)
                .and("active").is(true));
        mongoTemplate.updateFirst(query, new Update().set("lastLoginAt", at), UserDocument.class);
    }

    @Override
    public boolean linkAutoEmailIfUnlinked(String organizationId, String userId, String azureId,
                                           String azureEmail, Instant at) {
        Query query = Query.query(Criteria.where("_id").is(userId)
                .and("organizationId").is(organizationId)
                .and("active").is(true)
                .and("azureId").is(null));
        Update update = new Update()
                .set("azureId", azureId)
                .set("azureEmail", azureEmail)
                .set("azureIdentitySource", andromeda.sei.ms.feature.user.domain.AzureIdentitySource.AUTO_EMAIL)
                .set("azureIdentityLinkedAt", at)
                .set("updatedAt", at)
                .inc("version", 1);
        return mongoTemplate.updateFirst(query, update, UserDocument.class).getModifiedCount() > 0;
    }
}
