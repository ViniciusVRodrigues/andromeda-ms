package andromeda.sei.ms.feature.agent.infrastructure.persistence;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceAuthorization;
import org.springframework.stereotype.Component;

@Component
public class AgentDocumentMapper {

    public AgentDevice toDomain(AgentDeviceDocument d) {
        AgentDevice a = new AgentDevice();
        a.setId(d.getId());
        a.setOrganizationId(d.getOrganizationId());
        a.setUserId(d.getUserId());
        a.setMachineName(d.getMachineName());
        a.setOs(d.getOs());
        a.setOsVersion(d.getOsVersion());
        a.setOsLabel(d.getOsLabel());
        a.setArch(d.getArch());
        a.setTimezone(d.getTimezone());
        a.setAgentVersion(d.getAgentVersion());
        a.setPairedFromIp(d.getPairedFromIp());
        a.setAccessTokenHash(d.getAccessTokenHash());
        a.setAccessTokenExpiresAt(d.getAccessTokenExpiresAt());
        a.setRefreshTokenHash(d.getRefreshTokenHash());
        a.setRefreshTokenExpiresAt(d.getRefreshTokenExpiresAt());
        a.setPreviousRefreshHash(d.getPreviousRefreshHash());
        a.setPreviousRefreshValidUntil(d.getPreviousRefreshValidUntil());
        a.setPreviousRefreshGraceUsed(d.isPreviousRefreshGraceUsed());
        a.setPairedAt(d.getPairedAt());
        a.setCutoverDay(d.getCutoverDay());
        a.setLastSeenAt(d.getLastSeenAt());
        a.setLastUploadAt(d.getLastUploadAt());
        a.setPendingRows(d.getPendingRows());
        AgentDeviceDocument.ActiveTaskDocument t = d.getActiveTask();
        a.setActiveTask(t == null ? null : new AgentDevice.ActiveTask(t.getWorkItemId(), t.getTaskRef(),
                t.getTitle(), t.getSince(), t.getRepository(), t.getDay()));
        a.setSyncRequestedAt(d.getSyncRequestedAt());
        a.setSyncRequestedBy(d.getSyncRequestedBy());
        a.setSyncServedAt(d.getSyncServedAt());
        a.setRevokedAt(d.getRevokedAt());
        a.setRevokedBy(d.getRevokedBy());
        a.setActive(d.isActive());
        a.setVersion(d.getVersion());
        a.setCreatedAt(d.getCreatedAt());
        a.setUpdatedAt(d.getUpdatedAt());
        return a;
    }

    public AgentDeviceDocument toDocument(AgentDevice a) {
        AgentDevice.ActiveTask t = a.getActiveTask();
        return AgentDeviceDocument.builder()
                .id(a.getId())
                .organizationId(a.getOrganizationId())
                .userId(a.getUserId())
                .machineName(a.getMachineName())
                .os(a.getOs())
                .osVersion(a.getOsVersion())
                .osLabel(a.getOsLabel())
                .arch(a.getArch())
                .timezone(a.getTimezone())
                .agentVersion(a.getAgentVersion())
                .pairedFromIp(a.getPairedFromIp())
                .accessTokenHash(a.getAccessTokenHash())
                .accessTokenExpiresAt(a.getAccessTokenExpiresAt())
                .refreshTokenHash(a.getRefreshTokenHash())
                .refreshTokenExpiresAt(a.getRefreshTokenExpiresAt())
                .previousRefreshHash(a.getPreviousRefreshHash())
                .previousRefreshValidUntil(a.getPreviousRefreshValidUntil())
                .previousRefreshGraceUsed(a.isPreviousRefreshGraceUsed())
                .pairedAt(a.getPairedAt())
                .cutoverDay(a.getCutoverDay())
                .lastSeenAt(a.getLastSeenAt())
                .lastUploadAt(a.getLastUploadAt())
                .pendingRows(a.getPendingRows())
                .activeTask(t == null ? null : new AgentDeviceDocument.ActiveTaskDocument(t.workItemId(),
                        t.taskRef(), t.title(), t.since(), t.repository(), t.day()))
                .syncRequestedAt(a.getSyncRequestedAt())
                .syncRequestedBy(a.getSyncRequestedBy())
                .syncServedAt(a.getSyncServedAt())
                .revokedAt(a.getRevokedAt())
                .revokedBy(a.getRevokedBy())
                .active(a.isActive())
                .version(a.getVersion())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    public AgentDeviceAuthorization toDomain(AgentDeviceAuthorizationDocument d) {
        AgentDeviceAuthorization a = new AgentDeviceAuthorization();
        a.setId(d.getId());
        a.setOrganizationId(d.getOrganizationId());
        a.setUserId(d.getUserId());
        a.setDeviceId(d.getDeviceId());
        a.setDeviceCodeHash(d.getDeviceCodeHash());
        a.setUserCodeHash(d.getUserCodeHash());
        a.setMachineName(d.getMachineName());
        a.setOs(d.getOs());
        a.setOsVersion(d.getOsVersion());
        a.setOsLabel(d.getOsLabel());
        a.setArch(d.getArch());
        a.setTimezone(d.getTimezone());
        a.setAgentVersion(d.getAgentVersion());
        a.setIp(d.getIp());
        a.setStatus(AgentDeviceAuthorization.Status.valueOf(d.getStatus()));
        a.setIntervalSeconds(d.getIntervalSeconds());
        a.setLastPolledAt(d.getLastPolledAt());
        a.setExpiresAt(d.getExpiresAt());
        a.setPurgeAt(d.getPurgeAt());
        a.setActive(d.isActive());
        a.setVersion(d.getVersion());
        a.setCreatedAt(d.getCreatedAt());
        a.setUpdatedAt(d.getUpdatedAt());
        return a;
    }

    public AgentDeviceAuthorizationDocument toDocument(AgentDeviceAuthorization a) {
        return AgentDeviceAuthorizationDocument.builder()
                .id(a.getId())
                .organizationId(a.getOrganizationId())
                .userId(a.getUserId())
                .deviceId(a.getDeviceId())
                .deviceCodeHash(a.getDeviceCodeHash())
                .userCodeHash(a.getUserCodeHash())
                .machineName(a.getMachineName())
                .os(a.getOs())
                .osVersion(a.getOsVersion())
                .osLabel(a.getOsLabel())
                .arch(a.getArch())
                .timezone(a.getTimezone())
                .agentVersion(a.getAgentVersion())
                .ip(a.getIp())
                .status(a.getStatus().name())
                .intervalSeconds(a.getIntervalSeconds())
                .lastPolledAt(a.getLastPolledAt())
                .expiresAt(a.getExpiresAt())
                .purgeAt(a.getPurgeAt())
                .active(a.isActive())
                .version(a.getVersion())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
