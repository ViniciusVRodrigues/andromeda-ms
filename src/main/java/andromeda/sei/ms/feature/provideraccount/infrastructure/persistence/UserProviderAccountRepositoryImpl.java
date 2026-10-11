package andromeda.sei.ms.feature.provideraccount.infrastructure.persistence;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccount;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserProviderAccountRepositoryImpl implements UserProviderAccountRepository {

    private final UserProviderAccountMongoRepository mongoRepository;
    private final UserProviderAccountDocumentMapper mapper;

    @Override
    public UserProviderAccount save(UserProviderAccount account) {
        UserProviderAccountDocument doc = mapper.toDocument(account);
        Instant now = Instant.now();
        doc.setUpdatedAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }
        UserProviderAccountDocument saved = mongoRepository.save(doc);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UserProviderAccount> findByOrganizationIdAndUserIdAndProviderAndActiveTrue(
            String organizationId, String userId, Provider provider) {
        return mongoRepository.findByOrganizationIdAndUserIdAndProviderAndActiveTrue(organizationId, userId, provider)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<UserProviderAccount> findByOrganizationIdAndProviderAndExternalUserId(
            String organizationId, Provider provider, String externalUserId) {
        return mongoRepository.findByOrganizationIdAndProviderAndExternalUserId(organizationId, provider, externalUserId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<UserProviderAccount> findByOrganizationIdAndUserIdAndProvider(
            String organizationId, String userId, Provider provider) {
        return mongoRepository.findByOrganizationIdAndUserIdAndProvider(organizationId, userId, provider)
                .map(mapper::toDomain);
    }

    @Override
    public List<UserProviderAccount> findAllByOrganizationIdAndUserIdAndActiveTrue(
            String organizationId, String userId) {
        return mongoRepository.findAllByOrganizationIdAndUserIdAndActiveTrue(organizationId, userId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<UserProviderAccount> findAllByOrganizationIdAndActiveTrue(String organizationId) {
        return mongoRepository.findAllByOrganizationIdAndActiveTrue(organizationId)
                .stream().map(mapper::toDomain).toList();
    }
}
