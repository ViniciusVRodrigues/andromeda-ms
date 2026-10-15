package andromeda.sei.ms.feature.user.infrastructure.persistence;

import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.shared.domain.security.PermissionEffect;
import andromeda.sei.ms.shared.domain.security.PermissionOverride;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class UserDocumentMapper {

    public User toDomain(UserDocument doc) {
        User user = new User(
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
        user.setPermissionOverrides(toDomainOverrides(doc.getPermissionOverrides()));
        user.setLastLoginAt(doc.getLastLoginAt());
        return user;
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
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .permissionOverrides(toDocumentOverrides(user.getPermissionOverrides()));

        if (user.getId() != null) {
            builder.id(user.getId());
            builder.version(user.getVersion());
        }

        return builder.build();
    }

    private static List<PermissionOverride> toDomainOverrides(List<PermissionOverrideDocument> docs) {
        if (docs == null) return new ArrayList<>();
        return docs.stream()
                .filter(d -> d.getCode() != null && d.getEffect() != null)
                .map(d -> new PermissionOverride(d.getCode(), PermissionEffect.valueOf(d.getEffect()),
                        d.getProjectId(), d.getUpdatedBy(), d.getUpdatedAt()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static List<PermissionOverrideDocument> toDocumentOverrides(List<PermissionOverride> overrides) {
        if (overrides == null) return new ArrayList<>();
        return overrides.stream()
                .map(o -> new PermissionOverrideDocument(o.code(), o.effect().name(), o.projectId(),
                        o.updatedBy(), o.updatedAt()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
