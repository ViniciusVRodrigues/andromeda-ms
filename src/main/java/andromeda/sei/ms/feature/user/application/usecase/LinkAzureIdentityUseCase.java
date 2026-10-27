package andromeda.sei.ms.feature.user.application.usecase;

import andromeda.sei.ms.feature.allocation.application.usecase.ReattributeAzureActivityUseCase;
import andromeda.sei.ms.feature.user.domain.AzureIdentitySource;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.user.domain.exception.UserNotFoundException;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LinkAzureIdentityUseCase {

    private final UserRepository userRepository;
    private final WorkItemRepository workItemRepository;
    private final ReattributeAzureActivityUseCase reattributeActivity;

    public void execute(String orgId, String userId, String azureId, String azureEmail) {
        var user = userRepository.findByIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (azureId != null) {
            userRepository.findByOrganizationIdAndAzureId(orgId, azureId)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new ResourceConflictException(
                                "USER_AZURE_ID_TAKEN", "Azure ID already linked to another user");
                    });
        }

        user.setAzureId(azureId);
        user.setAzureEmail(azureEmail);
        Instant now = Instant.now();
        if (azureId == null) {
            user.setAzureIdentitySource(null);
            user.setAzureIdentityLinkedAt(null);
        } else {
            user.setAzureIdentitySource(AzureIdentitySource.ADMIN);
            user.setAzureIdentityLinkedAt(now);
        }
        user.setUpdatedAt(now);
        userRepository.save(user);

        workItemRepository.updateAssigneeUserIdByExternalIdOrEmail(orgId, azureId, user.getEmail(), userId);
        if (azureId != null) {
            reattributeActivity.execute(orgId, userId, azureId, user.getEmail());
        }
    }
}
