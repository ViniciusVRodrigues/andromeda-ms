package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.application.attribution.ActiveTaskHint;
import andromeda.sei.ms.feature.agent.application.attribution.AgentAttribution;
import andromeda.sei.ms.feature.agent.application.attribution.AgentTaskQuery;
import andromeda.sei.ms.feature.agent.application.attribution.AttributionSource;
import andromeda.sei.ms.feature.agent.application.attribution.ResolveAgentTaskUseCase;
import andromeda.sei.ms.feature.agent.application.attribution.ResolvedTask;
import andromeda.sei.ms.feature.agent.application.usage.AgentRowDispositionPolicy.Disposition;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.AgentUploadBatch;
import andromeda.sei.ms.feature.agent.domain.AgentUploadBatchRepository;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.Allocation;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.RowResult;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.Status;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTokenInvalidException;
import andromeda.sei.ms.feature.aiusage.application.usecase.CalculateAiCostUseCase;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentAIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.RegisterAgentAllocationUseCase;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Agent usage upload v2 (agente-endpoint-v2, design 4, 5.4 and 6). Per row: validation with reasons,
 * identity {@code (organization, device, key)}, replacement by the maximum per field (never a sum),
 * server-side task resolution only for new rows, and allocation without review of the live rows that
 * got a task. A batch replay answers the stored response. Rows of an existing key never change
 * {@code allocationId}, attribution, {@code day} or {@code active}: a manual correction wins.
 */
@Slf4j
@Component
public class IngestAgentUsageUseCase {

    static final String REASON_INVALID_FIELD = "INVALID_FIELD";
    private static final long MAX_TOKENS = 1_000_000_000_000L;
    private static final long MAX_INTERACTIONS = 100_000L;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9:._@/#-]{1,200}");
    private static final Pattern TOOL = Pattern.compile("[a-z0-9._-]{1,40}");
    private static final Pattern SESSION_HASH = Pattern.compile("[0-9a-fA-F]{16,64}");
    private static final Set<String> ORIGINS = Set.of("real", "estimado");
    private static final Set<String> METHODS = Set.of("explicit", "branch", "single-active", "none");

    private final AgentDeviceRepository devices;
    private final AgentUploadBatchRepository batches;
    private final AgentAIUsageRepository usages;
    private final ResolveAgentTaskUseCase resolveTask;
    private final AgentRowDispositionPolicy dispositionPolicy;
    private final RegisterAgentAllocationUseCase registerAllocation;
    private final AutoAssignAllocationUseCase autoAssign;
    private final CalculateAiCostUseCase costCalculator;
    private final OrganizationRepository organizations;
    private final Duration maxRowAge;
    private final Duration batchReplayTtl;
    private final boolean acceptEstimated;
    private final Clock clock;

    @Autowired
    public IngestAgentUsageUseCase(AgentDeviceRepository devices, AgentUploadBatchRepository batches,
                                   AgentAIUsageRepository usages, ResolveAgentTaskUseCase resolveTask,
                                   AgentRowDispositionPolicy dispositionPolicy,
                                   RegisterAgentAllocationUseCase registerAllocation,
                                   AutoAssignAllocationUseCase autoAssign, CalculateAiCostUseCase costCalculator,
                                   OrganizationRepository organizations,
                                   @Value("${andromeda.agent.max-row-age:P90D}") Duration maxRowAge,
                                   @Value("${andromeda.agent.batch-replay-ttl:P7D}") Duration batchReplayTtl,
                                   @Value("${andromeda.agent.accept-estimated:false}") boolean acceptEstimated) {
        this(devices, batches, usages, resolveTask, dispositionPolicy, registerAllocation, autoAssign,
                costCalculator, organizations, maxRowAge, batchReplayTtl, acceptEstimated, Clock.systemUTC());
    }

    IngestAgentUsageUseCase(AgentDeviceRepository devices, AgentUploadBatchRepository batches,
                            AgentAIUsageRepository usages, ResolveAgentTaskUseCase resolveTask,
                            AgentRowDispositionPolicy dispositionPolicy,
                            RegisterAgentAllocationUseCase registerAllocation,
                            AutoAssignAllocationUseCase autoAssign, CalculateAiCostUseCase costCalculator,
                            OrganizationRepository organizations, Duration maxRowAge, Duration batchReplayTtl,
                            boolean acceptEstimated, Clock clock) {
        this.devices = devices;
        this.batches = batches;
        this.usages = usages;
        this.resolveTask = resolveTask;
        this.dispositionPolicy = dispositionPolicy;
        this.registerAllocation = registerAllocation;
        this.autoAssign = autoAssign;
        this.costCalculator = costCalculator;
        this.organizations = organizations;
        this.maxRowAge = maxRowAge;
        this.batchReplayTtl = batchReplayTtl;
        this.acceptEstimated = acceptEstimated;
        this.clock = clock;
    }

    /** A row that passed validation, with the values the server derived. */
    private record Valid(int index, UsageBatchInput.Row row, Instant hour, LocalDate day, String branch,
                         long input, long output, long cacheRead, long cacheWrite, long thinking, int interactions) {
    }

    /** A row written now, waiting for the allocation phase. */
    private record Created(int index, AIUsage usage, ResolvedTask task, AttributionSource source,
                           Disposition disposition) {
    }

    public UsageUploadResult execute(AgentPrincipal principal, UsageBatchInput in) {
        String org = principal.organizationId();
        String deviceId = principal.deviceId();

        Optional<AgentUploadBatch> replay = batches.find(org, deviceId, in.batchId());
        if (replay.isPresent()) {
            return replay.get().getResponse();
        }

        AgentDevice device = devices.findActiveByIdAndOrganizationId(deviceId, org)
                .orElseThrow(AgentTokenInvalidException::new);
        Instant now = clock.instant();
        ZoneId zone = resolveZone(in.timezone(), device);

        List<UsageBatchInput.Row> rows = in.rows();
        RowResult[] results = new RowResult[rows.size()];
        List<Valid> valid = validate(rows, results, zone, now);

        Map<String, AIUsage> existing = valid.isEmpty() ? Map.of()
                : usages.findByKeys(org, deviceId, valid.stream().map(v -> v.row().key()).toList());
        List<AiPriceEntry> prices = valid.isEmpty() ? List.of() : prices(org);

        List<Created> created = new ArrayList<>();
        for (Valid v : valid) {
            try {
                process(principal, device, in, v, existing, prices, now, results, created);
            } catch (RuntimeException e) {
                log.warn("agent-usage: row failed org={} device={} key={}", org, deviceId, v.row().key(), e);
                results[v.index()] = RowResult.rejected(v.row().key(), "INTERNAL_ERROR", null, true);
            }
        }

        Set<LocalDate> daysToSuggest = allocate(principal, created, results);
        for (LocalDate day : daysToSuggest) {
            try {
                autoAssign.execute(org, principal.userId(), day);
            } catch (RuntimeException e) {
                log.warn("agent-usage: auto-assign failed org={} user={} day={}", org, principal.userId(), day, e);
            }
        }

        int accepted = 0;
        int duplicate = 0;
        int rejected = 0;
        for (RowResult r : results) {
            switch (r.status()) {
                case ACCEPTED -> accepted++;
                case DUPLICATE -> duplicate++;
                case REJECTED -> rejected++;
            }
        }
        if (accepted + duplicate > 0) {
            devices.touchUpload(deviceId, org, now);
        }
        UsageUploadResult response = new UsageUploadResult(in.batchId(), accepted, duplicate, rejected,
                List.of(results));
        batches.save(new AgentUploadBatch(org, deviceId, in.batchId(), response, now, now.plus(batchReplayTtl)));
        return response;
    }

    // ---- validation -------------------------------------------------------------------------

    private List<Valid> validate(List<UsageBatchInput.Row> rows, RowResult[] results, ZoneId zone, Instant now) {
        List<Valid> valid = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            UsageBatchInput.Row row = rows.get(i);
            String key = row.key();
            RowResult rejection = validateRow(row, zone, now);
            if (rejection == null && !seenKeys.add(key)) {
                rejection = RowResult.rejected(key, "DUPLICATE_KEY_IN_BATCH", null, false);
            }
            if (rejection != null) {
                results[i] = rejection;
                continue;
            }
            Instant hour = Instant.parse(row.hour());
            LocalDate day = row.day() != null && !row.day().isBlank()
                    ? LocalDate.parse(row.day()) : hour.atZone(zone).toLocalDate();
            String branch = row.branch() == null || row.branch().isBlank() || "HEAD".equalsIgnoreCase(row.branch().trim())
                    ? null : row.branch().trim();
            valid.add(new Valid(i, row, hour, day, branch, row.input(), row.output(),
                    nz(row.cacheRead()), nz(row.cacheWrite()), nz(row.thinking()), (int) nz(row.interactions())));
        }
        return valid;
    }

    private RowResult validateRow(UsageBatchInput.Row r, ZoneId zone, Instant now) {
        String key = r.key();
        if (!r.malformed().isEmpty()) {
            return invalid(key, r.malformed().iterator().next());
        }
        if (key == null || !KEY.matcher(key).matches()) return invalid(key, "key");
        if (r.tool() == null || !TOOL.matcher(r.tool()).matches()) return invalid(key, "tool");
        if (r.sessionHash() == null || !SESSION_HASH.matcher(r.sessionHash()).matches()) return invalid(key, "sessionHash");
        if (r.model() == null || r.model().isBlank() || r.model().length() > 120) return invalid(key, "model");
        if (r.hour() == null) return invalid(key, "hour");
        Instant hour;
        try {
            hour = Instant.parse(r.hour());
        } catch (DateTimeParseException e) {
            return invalid(key, "hour");
        }
        LocalDate explicitDay = null;
        if (r.day() != null && !r.day().isBlank()) {
            try {
                explicitDay = LocalDate.parse(r.day());
            } catch (DateTimeParseException e) {
                return invalid(key, "day");
            }
        }
        if (!inRange(r.input(), true, MAX_TOKENS)) return invalid(key, "tokens.input");
        if (!inRange(r.output(), true, MAX_TOKENS)) return invalid(key, "tokens.output");
        if (!inRange(r.cacheRead(), false, MAX_TOKENS)) return invalid(key, "tokens.cacheRead");
        if (!inRange(r.cacheWrite(), false, MAX_TOKENS)) return invalid(key, "tokens.cacheWrite");
        if (!inRange(r.thinking(), false, MAX_TOKENS)) return invalid(key, "tokens.thinking");
        if (!inRange(r.interactions(), false, MAX_INTERACTIONS)) return invalid(key, "interactions");
        if (r.origin() != null && !ORIGINS.contains(r.origin())) return invalid(key, "origin");
        if (r.repo() != null && !validRepo(r.repo())) return invalid(key, "repo");
        if (r.branch() != null && r.branch().length() > 255) return invalid(key, "branch");
        if (r.taskRef() != null && r.taskRef().length() > 64) return invalid(key, "taskRef");
        if (r.attributionMethod() != null && !METHODS.contains(r.attributionMethod())) return invalid(key, "attributionMethod");

        if (!hour.equals(hour.truncatedTo(ChronoUnit.HOURS))) {
            return RowResult.rejected(key, "BUCKET_NOT_ALIGNED", null, false);
        }
        if ("estimado".equals(r.origin()) && !acceptEstimated) {
            return RowResult.rejected(key, "ESTIMATED_NOT_ACCEPTED", null, false);
        }
        if (hour.isAfter(now.plus(Duration.ofHours(1)))) {
            return RowResult.rejected(key, "BUCKET_IN_FUTURE", null, true);
        }
        if (hour.isBefore(now.minus(maxRowAge))) {
            return RowResult.rejected(key, "ROW_TOO_OLD", null, false);
        }
        if (explicitDay != null) {
            LocalDate utcDay = hour.atZone(ZoneOffset.UTC).toLocalDate();
            long apart = Math.abs(ChronoUnit.DAYS.between(utcDay, explicitDay));
            if (apart > 1) return RowResult.rejected(key, "INVALID_DAY", null, false);
        }
        return null;
    }

    private static RowResult invalid(String key, String field) {
        return RowResult.rejected(key, REASON_INVALID_FIELD, field, false);
    }

    private static boolean inRange(Long value, boolean required, long max) {
        if (value == null) return !required;
        return value >= 0 && value <= max;
    }

    /** Normalized remote without protocol, credential, {@code .git} or local path. */
    private static boolean validRepo(String repo) {
        if (repo.isBlank() || repo.length() > 300) return false;
        if (repo.chars().anyMatch(Character::isWhitespace)) return false;
        if (repo.contains("://") || repo.contains("@") || repo.contains("\\") || repo.startsWith("/")) return false;
        return !repo.matches("^[A-Za-z]:.*");
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private ZoneId resolveZone(String batchTimezone, AgentDevice device) {
        if (batchTimezone != null && !batchTimezone.isBlank()) {
            try {
                ZoneId zone = ZoneId.of(batchTimezone.trim());
                if (!zone.getId().equals(device.getTimezone())) {
                    devices.updateTimezone(device.getId(), device.getOrganizationId(), zone.getId());
                    device.setTimezone(zone.getId());
                }
                return zone;
            } catch (DateTimeException ignored) {
                // an invalid zone is ignored: the one of the pairing stands
            }
        }
        try {
            return device.getTimezone() == null ? ZoneOffset.UTC : ZoneId.of(device.getTimezone());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    private List<AiPriceEntry> prices(String organizationId) {
        return organizations.findById(organizationId)
                .map(o -> o.getAiPrices() != null ? o.getAiPrices() : List.<AiPriceEntry>of())
                .orElse(List.of());
    }

    // ---- one row ----------------------------------------------------------------------------

    private void process(AgentPrincipal principal, AgentDevice device, UsageBatchInput in, Valid v,
                         Map<String, AIUsage> existing, List<AiPriceEntry> prices, Instant now,
                         RowResult[] results, List<Created> created) {
        String key = v.row().key();
        AIUsage current = existing.get(key);
        if (current == null) {
            Created row = insertNew(principal, device, in, v, prices, now);
            if (row != null) {
                created.add(row);
                return; // result is set after the allocation phase
            }
            // lost the race for the unique key: treat as existing
            current = usages.findByKeys(principal.organizationId(), principal.deviceId(), List.of(key)).get(key);
            if (current == null) {
                throw new IllegalStateException("row vanished after duplicate key");
            }
        }
        results[v.index()] = reconcileExisting(principal, device, v, current, prices, now);
    }

    private RowResult reconcileExisting(AgentPrincipal principal, AgentDevice device, Valid v, AIUsage current,
                                        List<AiPriceEntry> prices, Instant now) {
        long input = Math.max(current.getInputTokens(), v.input());
        long output = Math.max(current.getOutputTokens(), v.output());
        long cacheRead = Math.max(current.getCacheReadTokens(), v.cacheRead());
        long cacheWrite = Math.max(current.getCacheWriteTokens(), v.cacheWrite());
        long thinking = Math.max(current.getThinkingTokens(), v.thinking());
        int interactions = Math.max(current.getInteractions(), v.interactions());
        boolean grew = input > current.getInputTokens() || output > current.getOutputTokens()
                || cacheRead > current.getCacheReadTokens() || cacheWrite > current.getCacheWriteTokens()
                || thinking > current.getThinkingTokens() || interactions > current.getInteractions();
        Status status = Status.DUPLICATE;
        if (grew) {
            AIUsage values = new AIUsage(null, principal.organizationId(), current.getUserId(), null, null,
                    current.getDay(), current.getModel(), null, null, null, null, input, output, interactions,
                    null, null, null, now, true, null, now, now);
            values.setSource("AGENT");
            values.setCacheReadTokens(cacheRead);
            values.setCacheWriteTokens(cacheWrite);
            values.setThinkingTokens(thinking);
            values.setCostUsd(costCalculator.execute(values, prices));
            usages.raiseToMax(principal.organizationId(), current.getId(), values);
            status = Status.ACCEPTED;
        }
        return new RowResult(v.row().key(), status, labelOf(device, current), null, null, null, null, null);
    }

    private Allocation labelOf(AgentDevice device, AIUsage stored) {
        if (stored.getAllocationId() != null) return Allocation.ALLOCATED;
        if (!stored.isActive()) return Allocation.SUPERSEDED;
        Disposition d = dispositionPolicy.classify(device, stored.getUserId(), stored.getDay(), stored.getBucketStart(),
                stored.getTool());
        return d == Disposition.HISTORY ? Allocation.HISTORY : Allocation.QUEUED;
    }

    /** Returns null when the unique key was taken meanwhile. */
    private Created insertNew(AgentPrincipal principal, AgentDevice device, UsageBatchInput in, Valid v,
                              List<AiPriceEntry> prices, Instant now) {
        UsageBatchInput.Row r = v.row();
        Disposition disposition = dispositionPolicy.classify(device, principal.userId(), v.day(), v.hour(), r.tool());

        ResolvedTask task = null;
        AttributionSource source = AttributionSource.NONE;
        String repositoryProjectId = null;
        if (disposition != Disposition.SUPERSEDED) {
            AgentAttribution attribution = attribute(principal, device, r, v);
            if (attribution != null) {
                task = attribution.resolvedTask();
                source = attribution.source();
                repositoryProjectId = attribution.repositoryProjectId();
            }
        }

        AIUsage usage = new AIUsage(null, principal.organizationId(), principal.userId(), null, "agent", v.day(),
                r.model().trim(), "agent:" + sha256(r.key()), r.repo(), principal.deviceId(), device.getMachineName(),
                v.input(), v.output(), v.interactions(), r.tool(), null, null, now,
                disposition != Disposition.SUPERSEDED, null, now, now);
        usage.setSource("AGENT");
        usage.setAgentKey(r.key());
        usage.setDeviceId(principal.deviceId());
        usage.setTool(r.tool());
        usage.setSessionHash(r.sessionHash());
        usage.setBucketStart(v.hour());
        usage.setCacheReadTokens(v.cacheRead());
        usage.setCacheWriteTokens(v.cacheWrite());
        usage.setThinkingTokens(v.thinking());
        usage.setOrigin(r.origin() == null ? "real" : r.origin());
        usage.setRepository(r.repo());
        usage.setBranch(v.branch());
        usage.setBranchApproximate(Boolean.TRUE.equals(r.branchApproximate()));
        usage.setTaskRef(r.taskRef());
        usage.setAttributionMethod(r.attributionMethod() == null ? "none" : r.attributionMethod());
        usage.setAttributionSource(source.name());
        usage.setWorkItemId(task == null ? null : task.workItemId());
        usage.setProjectId(task != null && task.projectId() != null ? task.projectId() : repositoryProjectId);
        usage.setAgentVersion(in.agentVersion());
        usage.setBatchId(in.batchId());
        if (disposition == Disposition.SUPERSEDED) {
            usage.setSupersededBy("AIEF");
        }
        usage.setCostUsd(costCalculator.execute(usage, prices));

        Optional<AIUsage> inserted = usages.insert(usage);
        if (inserted.isEmpty()) return null;
        if (disposition != Disposition.SUPERSEDED) {
            dispositionPolicy.afterStored(device, principal.userId(), v.day(), r.tool());
        }
        return new Created(v.index(), inserted.get(), task, source, disposition);
    }

    private AgentAttribution attribute(AgentPrincipal principal, AgentDevice device, UsageBatchInput.Row r, Valid v) {
        ActiveTaskHint hint = device.getActiveTask() == null ? null
                : new ActiveTaskHint(device.getActiveTask().taskRef(), device.getActiveTask().since(),
                device.getActiveTask().repository());
        try {
            return resolveTask.execute(new AgentTaskQuery(principal.organizationId(), principal.userId(),
                    r.taskRef(), v.branch(), r.repo(), v.hour(), hint));
        } catch (RuntimeException e) {
            log.warn("agent-usage: task resolution failed org={} key={}", principal.organizationId(), r.key(), e);
            return null;
        }
    }

    // ---- allocation without review ----------------------------------------------------------

    /** Allocates the live rows with a task; returns the days left with rows to suggest. */
    private Set<LocalDate> allocate(AgentPrincipal principal, List<Created> created, RowResult[] results) {
        Set<LocalDate> toSuggest = new LinkedHashSet<>();
        Map<String, List<Created>> groups = new LinkedHashMap<>();
        for (Created c : created) {
            if (c.disposition() == Disposition.LIVE && c.task() != null) {
                groups.computeIfAbsent(c.usage().getDay() + "|" + c.task().workItemId(), k -> new ArrayList<>()).add(c);
            }
        }

        Set<Integer> allocated = new HashSet<>();
        for (List<Created> group : groups.values()) {
            Created first = group.get(0);
            try {
                registerAllocation.execute(new RegisterAgentAllocationUseCase.Registration(
                        principal.organizationId(), principal.userId(), first.usage().getDay(),
                        first.task().workItemId(), first.task().title(), first.task().projectId(),
                        group.stream().map(c -> c.usage().getId()).toList(), resolvedBy(group),
                        group.stream().map(c -> c.usage().getRepository()).toList(),
                        group.stream().map(c -> c.usage().getBranch()).toList()));
                group.forEach(c -> allocated.add(c.index()));
            } catch (RuntimeException e) {
                log.warn("agent-usage: allocation failed org={} user={} day={}", principal.organizationId(),
                        principal.userId(), first.usage().getDay(), e);
            }
        }

        for (Created c : created) {
            Allocation label;
            if (c.disposition() == Disposition.SUPERSEDED) {
                label = Allocation.SUPERSEDED;
            } else if (allocated.contains(c.index())) {
                label = Allocation.ALLOCATED;
            } else {
                label = c.disposition() == Disposition.HISTORY ? Allocation.HISTORY : Allocation.QUEUED;
                toSuggest.add(c.usage().getDay());
            }
            results[c.index()] = new RowResult(c.usage().getAgentKey(), Status.ACCEPTED, label,
                    c.task() == null ? null : c.task().workItemId(), c.task() == null ? null : c.task().ref(),
                    null, null, null);
        }
        return toSuggest;
    }

    private static AiUsageAllocation.ResolvedBy resolvedBy(List<Created> group) {
        AiUsageAllocation.ResolvedBy best = null;
        for (Created c : group) {
            AiUsageAllocation.ResolvedBy by = switch (c.source()) {
                case TASK_REF, ACTIVE_TASK -> AiUsageAllocation.ResolvedBy.MARK;
                case BRANCH -> AiUsageAllocation.ResolvedBy.BRANCH;
                case SINGLE_ACTIVE -> AiUsageAllocation.ResolvedBy.SINGLE_ACTIVE;
                case NONE -> null;
            };
            if (by != null && (best == null || by.ordinal() < best.ordinal())) best = by;
        }
        return best == null ? AiUsageAllocation.ResolvedBy.SINGLE_ACTIVE : best;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
