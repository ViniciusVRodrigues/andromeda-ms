package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.AgentMetaOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.DeviceOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.SyncRequestOutput;
import andromeda.sei.ms.feature.agent.application.usecase.GetAgentMetaUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.ListOwnDevicesUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.RequestDeviceSyncUseCase;
import andromeda.sei.ms.feature.agent.application.usecase.RevokeOwnDeviceUseCase;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The person's own devices and the static facts the screens need. */
@Tag(name = "Agent devices")
@RestController
@RequestMapping("/api/v1/agent")
@PreAuthorize("hasAuthority('CONNECTOR_TOKEN_MANAGE_OWN')")
public class AgentDevicesController {

    private final ListOwnDevicesUseCase listOwnDevices;
    private final RevokeOwnDeviceUseCase revokeOwnDevice;
    private final GetAgentMetaUseCase getAgentMeta;
    private final RequestDeviceSyncUseCase requestDeviceSync;

    public AgentDevicesController(ListOwnDevicesUseCase listOwnDevices, RevokeOwnDeviceUseCase revokeOwnDevice,
                                  GetAgentMetaUseCase getAgentMeta,
                                  RequestDeviceSyncUseCase requestDeviceSync) {
        this.listOwnDevices = listOwnDevices;
        this.revokeOwnDevice = revokeOwnDevice;
        this.getAgentMeta = getAgentMeta;
        this.requestDeviceSync = requestDeviceSync;
    }

    @GetMapping("/devices")
    public List<DeviceOutput> list(@AuthenticationPrincipal AndromedaUserDetails user) {
        return listOwnDevices.execute(user.getOrganizationId(), user.getUserId());
    }

    @DeleteMapping("/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AndromedaUserDetails user, @PathVariable String deviceId) {
        revokeOwnDevice.execute(user.getOrganizationId(), user.getUserId(), deviceId);
    }

    @PostMapping("/devices/{deviceId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyAuthority('CONNECTOR_TOKEN_MANAGE_OWN','CONNECTOR_TOKEN_MANAGE_ALL')")
    public SyncRequestOutput sync(@AuthenticationPrincipal AndromedaUserDetails user, @PathVariable String deviceId) {
        boolean all = user.getAuthorities().stream()
                .anyMatch(a -> "CONNECTOR_TOKEN_MANAGE_ALL".equals(a.getAuthority()));
        return requestDeviceSync.execute(user.getOrganizationId(), user.getUserId(), all, deviceId);
    }

    @GetMapping("/meta")
    public AgentMetaOutput meta(@AuthenticationPrincipal AndromedaUserDetails user) {
        return getAgentMeta.execute(user.getOrganizationId(), user.getUserId());
    }
}
