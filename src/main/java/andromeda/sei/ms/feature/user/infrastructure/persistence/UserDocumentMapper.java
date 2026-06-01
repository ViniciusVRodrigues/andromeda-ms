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
                doc.getGithubId(),
                doc.getInviteToken(),
                doc.isConfirmed(),
                doc.isActive(),
                doc.getVersion() != null ? doc.getVersion() : 0L,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    public UserDocument toDocument(User user) {
        return UserDocument.builder()
                .id(user.getId())
                .organizationId(user.getOrganizationId())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash())
                .name(user.getName())
                .roleId(user.getRoleId())
                .githubId(user.getGithubId())
                .inviteToken(user.getInviteToken())
                .confirmed(user.isConfirmed())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
