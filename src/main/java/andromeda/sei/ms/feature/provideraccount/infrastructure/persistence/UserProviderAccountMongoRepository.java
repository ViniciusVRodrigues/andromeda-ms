package andromeda.sei.ms.feature.provideraccount.infrastructure.persistence;

import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserProviderAccountMongoRepository extends MongoRepository<UserProviderAccountDocument, String> {

    Optional<UserProviderAccountDocument> findByOrganizationIdAndUserIdAndProviderAndActiveTrue(
            String organizationId, String userId, Provider provider);

    Optional<UserProviderAccountDocument> findByOrganizationIdAndProviderAndExternalUserId(
            String organizationId, Provider provider, String externalUserId);

    Optional<UserProviderAccountDocument> findByOrganizationIdAndUserIdAndProvider(
            String organizationId, String userId, Provider provider);

    List<UserProviderAccountDocument> findAllByOrganizationIdAndUserIdAndActiveTrue(
            String organizationId, String userId);
}
