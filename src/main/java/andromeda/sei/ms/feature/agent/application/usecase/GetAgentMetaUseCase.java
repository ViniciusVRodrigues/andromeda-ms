package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.AgentMetaOutput;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Static facts for the screens (package, versions, texts) from {@code andromeda.agent.*}. */
@Component
public class GetAgentMetaUseCase {

    private final AgentDeviceRepository devices;
    private final AgentVersionPolicy versionPolicy;
    private final String packageName;
    private final int nodeMin;
    private final String privacyUrl;
    private final String taskClearCommand;
    private final Clock clock;

    @Autowired
    public GetAgentMetaUseCase(AgentDeviceRepository devices, AgentVersionPolicy versionPolicy,
                               @Value("${andromeda.agent.package-name:andromeda-agent}") String packageName,
                               @Value("${andromeda.agent.node-min:22}") int nodeMin,
                               @Value("${andromeda.agent.privacy-url:}") String privacyUrl,
                               @Value("${andromeda.agent.task-clear-command:andromeda task --clear}") String taskClearCommand) {
        this(devices, versionPolicy, packageName, nodeMin, privacyUrl, taskClearCommand, Clock.systemUTC());
    }

    GetAgentMetaUseCase(AgentDeviceRepository devices, AgentVersionPolicy versionPolicy, String packageName,
                        int nodeMin, String privacyUrl, String taskClearCommand, Clock clock) {
        this.devices = devices;
        this.versionPolicy = versionPolicy;
        this.packageName = packageName;
        this.nodeMin = nodeMin;
        this.privacyUrl = privacyUrl;
        this.taskClearCommand = taskClearCommand;
        this.clock = clock;
    }

    public AgentMetaOutput execute(String organizationId, String userId) {
        return new AgentMetaOutput(packageName, versionPolicy.recommendedVersion(), versionPolicy.recommendedVersion(),
                versionPolicy.minVersion(), nodeMin, privacyUrl, taskClearCommand, aiefIgnored(organizationId, userId));
    }

    /**
     * The person has an active device whose cutover day has arrived (in the time zone of the device):
     * from then on the agent counts and the AIEF upload is ignored. Package 4 refines the cutover day.
     */
    private boolean aiefIgnored(String organizationId, String userId) {
        Instant now = clock.instant();
        return devices.findDevicesOfUser(organizationId, userId).stream()
                .filter(d -> !d.isRevoked() && d.getCutoverDay() != null && d.getTimezone() != null)
                .anyMatch(d -> {
                    LocalDate today = now.atZone(ZoneId.of(d.getTimezone())).toLocalDate();
                    return !d.getCutoverDay().isAfter(today);
                });
    }
}
