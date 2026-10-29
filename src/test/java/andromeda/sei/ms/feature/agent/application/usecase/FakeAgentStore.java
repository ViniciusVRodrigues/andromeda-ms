package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorizationRepository;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory stand-in of both agent ports, for use case tests. */
class FakeAgentStore implements AgentDeviceRepository, AgentDeviceAuthorizationRepository {

    final Map<String, AgentDevice> devices = new LinkedHashMap<>();
    final Map<String, AgentDeviceAuthorization> authorizations = new LinkedHashMap<>();
    int touchSeenCalls;
    int touchUploadCalls;
    private int seq;

    @Override
    public AgentDevice save(AgentDevice device) {
        if (device.getId() == null) device.setId("dev-" + (++seq));
        devices.put(device.getId(), device);
        return device;
    }

    @Override
    public boolean existsByOrganizationId(String organizationId) {
        return devices.values().stream().anyMatch(d -> Objects.equals(d.getOrganizationId(), organizationId));
    }

    @Override
    public Optional<AgentDevice> findActiveByIdAndOrganizationId(String id, String organizationId) {
        return findByIdAndOrganizationId(id, organizationId).filter(d -> !d.isRevoked());
    }

    @Override
    public Optional<AgentDevice> findByIdAndOrganizationId(String id, String organizationId) {
        return Optional.ofNullable(devices.get(id)).filter(d -> Objects.equals(d.getOrganizationId(), organizationId));
    }

    @Override
    public Optional<AgentDevice> findByAccessTokenHash(String hash) {
        return devices.values().stream().filter(d -> hash.equals(d.getAccessTokenHash())).findFirst();
    }

    @Override
    public Optional<AgentDevice> findByRefreshTokenHash(String hash) {
        return devices.values().stream().filter(d -> hash.equals(d.getRefreshTokenHash())).findFirst();
    }

    @Override
    public Optional<AgentDevice> findByPreviousRefreshHash(String hash) {
        return devices.values().stream().filter(d -> hash.equals(d.getPreviousRefreshHash())).findFirst();
    }

    @Override
    public List<AgentDevice> findDevicesOfUser(String organizationId, String userId) {
        return new ArrayList<>(devices.values().stream()
                .filter(d -> organizationId.equals(d.getOrganizationId()) && userId.equals(d.getUserId())).toList());
    }

    @Override
    public void touchSeen(String deviceId, String organizationId, Instant seenAt, String agentVersion) {
        touchSeenCalls++;
        AgentDevice d = devices.get(deviceId);
        d.setLastSeenAt(seenAt);
        if (agentVersion != null) d.setAgentVersion(agentVersion);
    }

    @Override
    public void recordHeartbeat(String deviceId, String organizationId, Instant seenAt, String agentVersion,
                                Integer pendingRows, String timezone) {
        AgentDevice d = devices.get(deviceId);
        d.setLastSeenAt(seenAt);
        if (agentVersion != null) d.setAgentVersion(agentVersion);
        if (pendingRows != null) d.setPendingRows(pendingRows);
        if (timezone != null) d.setTimezone(timezone);
    }

    @Override
    public void updateTimezone(String deviceId, String organizationId, String timezone) {
        devices.get(deviceId).setTimezone(timezone);
    }

    @Override
    public void touchUpload(String deviceId, String organizationId, Instant uploadAt) {
        touchUploadCalls++;
        devices.get(deviceId).setLastUploadAt(uploadAt);
    }

    @Override
    public void setActiveTask(String deviceId, String organizationId, AgentDevice.ActiveTask task) {
        devices.get(deviceId).setActiveTask(task);
    }

    @Override
    public AgentDeviceAuthorization save(AgentDeviceAuthorization a) {
        if (a.getId() == null) a.setId("auth-" + (++seq));
        authorizations.put(a.getId(), a);
        return a;
    }

    @Override
    public Optional<AgentDeviceAuthorization> findByDeviceCodeHash(String hash) {
        return authorizations.values().stream().filter(a -> hash.equals(a.getDeviceCodeHash())).findFirst();
    }

    @Override
    public Optional<AgentDeviceAuthorization> findByUserCodeHash(String hash) {
        return authorizations.values().stream().filter(a -> hash.equals(a.getUserCodeHash())).findFirst();
    }
}
