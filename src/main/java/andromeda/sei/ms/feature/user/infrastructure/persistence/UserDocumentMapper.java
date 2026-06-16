package andromeda.sei.ms.feature.user.infrastructure.persistence;

import andromeda.sei.ms.feature.user.domain.User;
import org.springframework.stereotype.Component;

@Component
public class UserDocumentMapper {

    public User toDomain(UserDocument doc) {
        return new User(
                doc.getId(),
                doc.getOrganizationId(),
                doc.getEmail(),
                doc.getPasswordHash(),
                doc.getName(),
                doc.getRoleId(),
                doc.getSeniority(),
                doc.getGithubId(),
                doc.getAzureId(),
                doc.getAzureEmail(),
                doc.getInviteToken(),
                doc.isConfirmed(),
                doc.isActive(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public UserDocument toDocument(User user) {
        var builder = UserDocument.builder()
                .organizationId(user.getOrganizationId())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash())
                .name(user.getName())
                .roleId(user.getRoleId())
                .seniority(user.getSeniority())
                .githubId(user.getGithubId())
                .azureId(user.getAzureId())
                .azureEmail(user.getAzureEmail())
                .inviteToken(user.getInviteToken())
                .confirmed(user.isConfirmed())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt());

        if (user.getId() != null) {
            builder.id(user.getId());
            builder.version(user.getVersion());
        }

        return builder.build();
    }
}
