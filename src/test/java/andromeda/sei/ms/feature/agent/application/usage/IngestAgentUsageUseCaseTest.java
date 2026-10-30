package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.application.attribution.AgentAttribution;
import andromeda.sei.ms.feature.agent.application.attribution.AgentTaskQuery;
import andromeda.sei.ms.feature.agent.application.attribution.AttributionSource;
import andromeda.sei.ms.feature.agent.application.attribution.ResolveAgentTaskUseCase;
import andromeda.sei.ms.feature.agent.application.attribution.ResolvedTask;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.AgentUploadBatch;
import andromeda.sei.ms.feature.agent.domain.AgentUploadBatchRepository;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult;
import andromeda.sei.ms.feature.agent.domain.exception.AgentBatchInProgressException;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.Allocation;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.RowResult;
import andromeda.sei.ms.feature.agent.domain.UsageUploadResult.Status;
import andromeda.sei.ms.feature.aiusage.application.usecase.CalculateAiCostUseCase;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentAIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.RegisterAgentAllocationUseCase;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestAgentUsageUseCaseTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final String DEVICE = "dev-1";
    static final Instant NOW = Instant.parse("2026-10-29T15:30:00Z");
    static final String BATCH = "8a3e0000-0000-4000-8000-000000000001";

    AgentDeviceRepository devices;
    InMemoryBatches batches;
    InMemoryUsages usages;
    ResolveAgentTaskUseCase resolveTask;
    RegisterAgentAllocationUseCase registerAllocation;
    AutoAssignAllocationUseCase autoAssign;
    OrganizationRepository organizations;
    AgentDevice device;
    IngestAgentUsageUseCase useCase;
    AgentPrincipal principal = new AgentPrincipal(ORG, USER, DEVICE);

    @BeforeEach
    void setUp() {
        devices = mock(AgentDeviceRepository.class);
        batches = new InMemoryBatches();
        usages = new InMemoryUsages();
        resolveTask = mock(ResolveAgentTaskUseCase.class);
        registerAllocation = mock(RegisterAgentAllocationUseCase.class);
        autoAssign = mock(AutoAssignAllocationUseCase.class);
        organizations = mock(OrganizationRepository.class);
        device = new AgentDevice();
        device.setId(DEVICE);
        device.setOrganizationId(ORG);
        device.setUserId(USER);
        device.setMachineName("NOTE");
        device.setTimezone("America/Sao_Paulo");
        device.setPairedAt(Instant.parse("2026-10-01T00:00:00Z"));
        when(devices.findActiveByIdAndOrganizationId(DEVICE, ORG)).thenReturn(Optional.of(device));
        when(organizations.findById(ORG)).thenReturn(Optional.empty());
        when(resolveTask.execute(any())).thenReturn(new AgentAttribution(null, null, AttributionSource.NONE));
        useCase = new IngestAgentUsageUseCase(devices, batches, usages, resolveTask, new PairingRowDispositionPolicy(),
                registerAllocation, autoAssign, new CalculateAiCostUseCase(), organizations, Duration.ofDays(90),
                Duration.ofDays(7), false, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- idempotency ------------------------------------------------------------------------

    @Test
    void should_answerTheStoredResponseWithoutReprocessing_when_theSameBatchIsResent() {
        UsageBatchInput in = batch(row("k1", 100, 200));

        UsageUploadResult first = useCase.execute(principal, in);
        UsageUploadResult second = useCase.execute(principal, in);

        assertThat(second).isEqualTo(first);
        assertThat(first.accepted()).isEqualTo(1);
        assertThat(usages.inserts).isEqualTo(1);
        verify(devices, times(1)).touchUpload(eq(DEVICE), eq(ORG), any());
    }

    @Test
    void should_answer409WithoutProcessing_when_theSameBatchIsResentWhileTheFirstStillRuns() {
        batches.runningSince(BATCH, NOW.minusSeconds(5));

        assertThatThrownBy(() -> useCase.execute(principal, batch(row("k1", 100, 200))))
                .isInstanceOf(AgentBatchInProgressException.class)
                .satisfies(e -> assertThat(((AgentBatchInProgressException) e).getRetryAfterSeconds()).isEqualTo(30));
        assertThat(usages.inserts).isZero();
        verify(devices, never()).touchUpload(any(), any(), any());
    }

    @Test
    void should_answerTheFirstRequestsResponse_when_itFinishesWhileTheResendWaits() {
        UsageUploadResult firstResponse = new UsageUploadResult(BATCH, 1, 0, 0, List.of());
        batches.runningSince(BATCH, NOW.minusSeconds(5));
        batches.completesWhileWaiting = firstResponse;
        useCase = new IngestAgentUsageUseCase(devices, batches, usages, resolveTask, new PairingRowDispositionPolicy(),
                registerAllocation, autoAssign, new CalculateAiCostUseCase(), organizations, Duration.ofDays(90),
                Duration.ofDays(7), false, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10),
                Duration.ofSeconds(1));

        UsageUploadResult second = useCase.execute(principal, batch(row("k1", 100, 200)));

        assertThat(second).isEqualTo(firstResponse);
        assertThat(usages.inserts).isZero();
    }

    @Test
    void should_takeTheBatchOver_when_theFirstClaimIsOlderThanTheLease() {
        batches.runningSince(BATCH, NOW.minus(Duration.ofMinutes(11)));

        UsageUploadResult result = useCase.execute(principal, batch(row("k1", 100, 200)));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(usages.inserts).isEqualTo(1);
        assertThat(batches.find(ORG, DEVICE, BATCH).orElseThrow().getResponse()).isEqualTo(result);
    }

    @Test
    void should_releaseTheClaim_when_processingFails_soTheResendIsProcessed() {
        doThrow(new IllegalStateException("mongo caiu")).when(devices).touchUpload(eq(DEVICE), eq(ORG), any());

        assertThatThrownBy(() -> useCase.execute(principal, batch(row("k1", 100, 200))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(batches.releases).isEqualTo(1);
        assertThat(batches.find(ORG, DEVICE, BATCH)).isEmpty();

        doNothing().when(devices).touchUpload(eq(DEVICE), eq(ORG), any());
        assertThat(useCase.execute(principal, batch(row("k1", 100, 200))).results()).hasSize(1);
    }

    @Test
    void should_keepTheMaximum_when_aBucketGrowsAndAnOlderVersionArrivesAfter() {
        useCase.execute(principal, batch(BATCH, row("k1", 10, 3400)));
        UsageUploadResult grown = useCase.execute(principal, batch("8a3e0000-0000-4000-8000-000000000002", row("k1", 10, 5100)));
        UsageUploadResult older = useCase.execute(principal, batch("8a3e0000-0000-4000-8000-000000000003", row("k1", 10, 3400)));

        assertThat(grown.results().get(0).status()).isEqualTo(Status.ACCEPTED);
        assertThat(usages.byKey.get("k1").getOutputTokens()).isEqualTo(5100);
        assertThat(older.results().get(0).status()).isEqualTo(Status.DUPLICATE);
        assertThat(older.duplicate()).isEqualTo(1);
        assertThat(usages.byKey.get("k1").getOutputTokens()).isEqualTo(5100);
    }

    @Test
    void should_neverSumTokens_when_aSecondVersionHasOtherFieldsHigher() {
        useCase.execute(principal, batch(BATCH, rowWith("k1", 100, 200, 50, 0)));
        useCase.execute(principal, batch("8a3e0000-0000-4000-8000-000000000002", rowWith("k1", 90, 300, 40, 7)));

        AIUsage stored = usages.byKey.get("k1");
        assertThat(stored.getInputTokens()).isEqualTo(100);
        assertThat(stored.getOutputTokens()).isEqualTo(300);
        assertThat(stored.getCacheReadTokens()).isEqualTo(50);
        assertThat(stored.getCacheWriteTokens()).isEqualTo(7);
    }

    @Test
    void should_returnDuplicateAndLeaveTheAllocationIntact_when_anOldBatchOfAnAlreadyAllocatedDayIsResent() {
        AIUsage allocated = storedRow("k1", 100, 200);
        allocated.setAllocationId("alloc-agent");
        usages.byKey.put("k1", allocated);

        UsageUploadResult result = useCase.execute(principal, batch(row("k1", 100, 200)));

        RowResult r = result.results().get(0);
        assertThat(r.status()).isEqualTo(Status.DUPLICATE);
        assertThat(r.allocation()).isEqualTo(Allocation.ALLOCATED);
        assertThat(allocated.getAllocationId()).isEqualTo("alloc-agent");
        verify(registerAllocation, never()).execute(any());
        verify(autoAssign, never()).execute(any(), any(), any());
        assertThat(usages.raises).isZero();
    }

    @Test
    void should_keepTheManualAllocationAndUpdateTheNumbers_when_aRowOfAManualAllocationIsResentWithMoreTokens() {
        AIUsage inManual = storedRow("k1", 100, 200);
        inManual.setAllocationId("alloc-manual");
        usages.byKey.put("k1", inManual);

        UsageUploadResult result = useCase.execute(principal, batch(row("k1", 100, 900)));

        assertThat(result.results().get(0).status()).isEqualTo(Status.ACCEPTED);
        assertThat(inManual.getOutputTokens()).isEqualTo(900);
        assertThat(inManual.getAllocationId()).isEqualTo("alloc-manual");
        verify(registerAllocation, never()).execute(any());
    }

    // ---- validation -------------------------------------------------------------------------

    @Test
    void should_acceptTheValidRowsAndRejectTheOthersWithReasons_when_theBatchIsPartiallyInvalid() {
        UsageUploadResult result = useCase.execute(principal, batch(
                row("ok", 1, 2),
                withDay(row("badday", 1, 2), "2026-10-20"),
                withHour(row("future", 1, 2), "2026-10-29T17:00:00Z"),
                withHour(row("old", 1, 2), "2026-05-01T10:00:00Z"),
                withHour(row("unaligned", 1, 2), "2026-10-29T10:30:00Z"),
                withOrigin(row("est", 1, 2), "estimado"),
                row("bad key!", 1, 2)));

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(6);
        assertThat(reason(result, "badday")).isEqualTo("INVALID_DAY");
        assertThat(reason(result, "future")).isEqualTo("BUCKET_IN_FUTURE");
        assertThat(retryable(result, "future")).isTrue();
        assertThat(reason(result, "old")).isEqualTo("ROW_TOO_OLD");
        assertThat(retryable(result, "old")).isFalse();
        assertThat(reason(result, "unaligned")).isEqualTo("BUCKET_NOT_ALIGNED");
        assertThat(reason(result, "est")).isEqualTo("ESTIMATED_NOT_ACCEPTED");
        RowResult badKey = result.results().get(6);
        assertThat(badKey.reason()).isEqualTo("INVALID_FIELD");
        assertThat(badKey.field()).isEqualTo("key");
        assertThat(usages.inserts).isEqualTo(1);
    }

    @Test
    void should_rejectTheSecondOccurrenceOfAKey_when_theBatchRepeatsIt() {
        UsageUploadResult result = useCase.execute(principal, batch(row("k1", 1, 2), row("k1", 5, 9)));

        assertThat(result.results().get(0).status()).isEqualTo(Status.ACCEPTED);
        assertThat(reason(result, 1)).isEqualTo("DUPLICATE_KEY_IN_BATCH");
        assertThat(retryable(result, 1)).isFalse();
        assertThat(usages.byKey.get("k1").getOutputTokens()).isEqualTo(2);
    }

    @Test
    void should_rejectTheRowWithTheMalformedField_when_aValueHasTheWrongType() {
        UsageBatchInput.Row bad = new UsageBatchInput.Row("k1", "claude-code", "9f2c9f2c9f2c9f2c", "claude-sonnet-5-5",
                "2026-10-29T14:00:00Z", null, null, 5L, null, null, null, null, null, null, null, null, null,
                Set.of("tokens.input"));

        UsageUploadResult result = useCase.execute(principal, batch(bad));

        assertThat(result.results().get(0).field()).isEqualTo("tokens.input");
        assertThat(result.rejected()).isEqualTo(1);
    }

    @Test
    void should_deriveTheLocalDayFromTheHourAndTheDeviceTimezone() {
        useCase.execute(principal, batch(withHour(row("k1", 1, 2), "2026-10-29T01:00:00Z")));

        assertThat(usages.byKey.get("k1").getDay()).isEqualTo(LocalDate.of(2026, 10, 28)); // UTC-3
    }

    @Test
    void should_useTheDayOfTheRow_when_itIsWithinOneDayOfTheUtcDate() {
        useCase.execute(principal, batch(withDay(withHour(row("k1", 1, 2), "2026-10-29T01:00:00Z"), "2026-10-29")));

        assertThat(usages.byKey.get("k1").getDay()).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    @Test
    void should_useTheTimezoneOfTheBatchAndUpdateTheDevice() {
        UsageBatchInput in = new UsageBatchInput(BATCH, "0.1.0", "Asia/Tokyo",
                List.of(withHour(row("k1", 1, 2), "2026-10-29T16:00:00Z")));

        useCase.execute(principal, in);

        assertThat(usages.byKey.get("k1").getDay()).isEqualTo(LocalDate.of(2026, 10, 30)); // UTC+9
        verify(devices).updateTimezone(DEVICE, ORG, "Asia/Tokyo");
    }

    @Test
    void should_treatHeadAsNoBranch_andPassTheRowFieldsToTheResolver() {
        useCase.execute(principal, batch(withBranch(row("k1", 1, 2), "HEAD")));

        ArgumentCaptor<AgentTaskQuery> captor = ArgumentCaptor.forClass(AgentTaskQuery.class);
        verify(resolveTask).execute(captor.capture());
        assertThat(captor.getValue().branch()).isNull();
        assertThat(captor.getValue().userId()).isEqualTo(USER);
        assertThat(usages.byKey.get("k1").getBranch()).isNull();
    }

    // ---- attribution and allocation ---------------------------------------------------------

    @Test
    void should_allocateWithoutReview_when_aLiveNewRowResolvesATask() {
        when(resolveTask.execute(any())).thenReturn(new AgentAttribution(
                new ResolvedTask("wi-1", "proj-1", "Fix login", "AB#123", AttributionSource.BRANCH), "proj-1",
                AttributionSource.BRANCH));
        when(registerAllocation.execute(any())).thenReturn("alloc-1");

        UsageUploadResult result = useCase.execute(principal, batch(
                withBranch(row("k1", 1, 2), "feature/AB#123-x"), withBranch(row("k2", 3, 4), "feature/AB#123-x")));

        RowResult r = result.results().get(0);
        assertThat(r.allocation()).isEqualTo(Allocation.ALLOCATED);
        assertThat(r.workItemId()).isEqualTo("wi-1");
        assertThat(r.taskRef()).isEqualTo("AB#123");
        ArgumentCaptor<RegisterAgentAllocationUseCase.Registration> captor =
                ArgumentCaptor.forClass(RegisterAgentAllocationUseCase.Registration.class);
        verify(registerAllocation, times(1)).execute(captor.capture());
        RegisterAgentAllocationUseCase.Registration reg = captor.getValue();
        assertThat(reg.workItemId()).isEqualTo("wi-1");
        assertThat(reg.usageIds()).hasSize(2);
        assertThat(reg.resolvedBy()).isEqualTo(AiUsageAllocation.ResolvedBy.BRANCH);
        assertThat(reg.branches()).containsOnly("feature/AB#123-x");
        verify(autoAssign, never()).execute(any(), any(), any());
        AIUsage stored = usages.byKey.get("k1");
        assertThat(stored.getWorkItemId()).isEqualTo("wi-1");
        assertThat(stored.getProjectId()).isEqualTo("proj-1");
        assertThat(stored.getAttributionSource()).isEqualTo("BRANCH");
    }

    @Test
    void should_queueAndSuggest_when_aLiveNewRowHasNoTask() {
        UsageUploadResult result = useCase.execute(principal, batch(row("k1", 1, 2)));

        assertThat(result.results().get(0).allocation()).isEqualTo(Allocation.QUEUED);
        verify(registerAllocation, never()).execute(any());
        verify(autoAssign).execute(ORG, USER, LocalDate.of(2026, 10, 29));
        assertThat(usages.byKey.get("k1").getAllocationId()).isNull();
    }

    @Test
    void should_neverAllocateAutomatically_when_theHourIsBeforeThePairing() {
        device.setPairedAt(Instant.parse("2026-10-29T15:10:00Z"));
        when(resolveTask.execute(any())).thenReturn(new AgentAttribution(
                new ResolvedTask("wi-1", "proj-1", "T", "AB#1", AttributionSource.TASK_REF), "proj-1",
                AttributionSource.TASK_REF));

        UsageUploadResult result = useCase.execute(principal, batch(
                withHour(row("hist", 1, 2), "2026-10-29T14:00:00Z"), withHour(row("live", 1, 2), "2026-10-29T15:00:00Z")));

        assertThat(result.results().get(0).allocation()).isEqualTo(Allocation.HISTORY);
        assertThat(result.results().get(1).allocation()).isEqualTo(Allocation.ALLOCATED);
        assertThat(usages.byKey.get("hist").getWorkItemId()).isEqualTo("wi-1"); // task kept on the row
        ArgumentCaptor<RegisterAgentAllocationUseCase.Registration> captor =
                ArgumentCaptor.forClass(RegisterAgentAllocationUseCase.Registration.class);
        verify(registerAllocation, times(1)).execute(captor.capture());
        assertThat(captor.getValue().usageIds()).containsExactly(usages.byKey.get("live").getId());
        verify(autoAssign).execute(ORG, USER, LocalDate.of(2026, 10, 29));
    }

    @Test
    void should_storeInactiveAndNotAllocate_when_thePolicySaysSuperseded() {
        AgentRowDispositionPolicy superseded = (d, u, day, hour) -> AgentRowDispositionPolicy.Disposition.SUPERSEDED;
        useCase = new IngestAgentUsageUseCase(devices, batches, usages, resolveTask, superseded, registerAllocation,
                autoAssign, new CalculateAiCostUseCase(), organizations, Duration.ofDays(90), Duration.ofDays(7),
                false, Clock.fixed(NOW, ZoneOffset.UTC));

        UsageUploadResult result = useCase.execute(principal, batch(row("k1", 1, 2)));

        assertThat(result.results().get(0).allocation()).isEqualTo(Allocation.SUPERSEDED);
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(usages.byKey.get("k1").isActive()).isFalse();
        assertThat(usages.byKey.get("k1").getSupersededBy()).isEqualTo("AIEF");
        verify(resolveTask, never()).execute(any());
        verify(registerAllocation, never()).execute(any());
        verify(autoAssign, never()).execute(any(), any(), any());
    }

    @Test
    void should_callAfterStoredWithTheTool_when_aRowThatCountsIsStored() {
        java.util.List<String> calls = new java.util.ArrayList<>();
        AgentRowDispositionPolicy live = new AgentRowDispositionPolicy() {
            @Override
            public Disposition classify(AgentDevice d, String u, java.time.LocalDate day, Instant hour) {
                return Disposition.LIVE;
            }

            @Override
            public void afterStored(AgentDevice d, String u, java.time.LocalDate day, String tool) {
                calls.add(u + "|" + tool);
            }
        };
        useCase = new IngestAgentUsageUseCase(devices, batches, usages, resolveTask, live, registerAllocation,
                autoAssign, new CalculateAiCostUseCase(), organizations, Duration.ofDays(90), Duration.ofDays(7),
                false, Clock.fixed(NOW, ZoneOffset.UTC));

        useCase.execute(principal, batch(row("k1", 1, 2)));

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0)).startsWith(USER + "|");
    }

    @Test
    void should_notCallAfterStored_when_theRowIsSuperseded() {
        java.util.List<String> calls = new java.util.ArrayList<>();
        AgentRowDispositionPolicy superseded = new AgentRowDispositionPolicy() {
            @Override
            public Disposition classify(AgentDevice d, String u, java.time.LocalDate day, Instant hour) {
                return Disposition.SUPERSEDED;
            }

            @Override
            public void afterStored(AgentDevice d, String u, java.time.LocalDate day, String tool) {
                calls.add("called");
            }
        };
        useCase = new IngestAgentUsageUseCase(devices, batches, usages, resolveTask, superseded, registerAllocation,
                autoAssign, new CalculateAiCostUseCase(), organizations, Duration.ofDays(90), Duration.ofDays(7),
                false, Clock.fixed(NOW, ZoneOffset.UTC));

        useCase.execute(principal, batch(row("k1", 1, 2)));

        assertThat(calls).isEmpty();
        assertThat(usages.byKey.get("k1").getSupersededBy()).isEqualTo("AIEF");
    }

    @Test
    void should_notResolveTheTaskAgain_when_theRowAlreadyExists() {
        useCase.execute(principal, batch(row("k1", 1, 2)));
        org.mockito.Mockito.clearInvocations(resolveTask);

        useCase.execute(principal, batch("8a3e0000-0000-4000-8000-000000000002", row("k1", 1, 3)));

        verify(resolveTask, never()).execute(any());
    }

    @Test
    void should_storeBranchApproximate_trueWhenSent_falseWhenAbsent_andKeepTheFirstOnResend() {
        UsageBatchInput.Row r = row("k1", 1, 2);
        UsageBatchInput.Row approx = new UsageBatchInput.Row(r.key(), r.tool(), r.sessionHash(), r.model(), r.hour(),
                r.day(), r.input(), r.output(), r.cacheRead(), r.cacheWrite(), r.thinking(), r.interactions(),
                r.origin(), r.repo(), r.branch(), r.taskRef(), r.attributionMethod(), true, r.malformed());
        useCase.execute(principal, batch(approx, row("k2", 1, 2)));
        assertThat(usages.byKey.get("k1").isBranchApproximate()).isTrue();
        assertThat(usages.byKey.get("k2").isBranchApproximate()).isFalse();

        useCase.execute(principal, batch("8a3e0000-0000-4000-8000-000000000002", row("k1", 1, 9)));
        assertThat(usages.byKey.get("k1").isBranchApproximate()).isTrue();
    }

    // ---- delivery ---------------------------------------------------------------------------

    @Test
    void should_touchLastUploadOnlyWhenAtLeastOneRowWasDelivered() {
        useCase.execute(principal, batch(withDay(row("bad", 1, 2), "2026-10-01")));
        verify(devices, never()).touchUpload(any(), any(), any());

        useCase.execute(principal, batch("8a3e0000-0000-4000-8000-000000000002", row("k1", 1, 2)));
        verify(devices).touchUpload(DEVICE, ORG, NOW);
    }

    @Test
    void should_countTheCostLikeTheAief_whenThereIsAPriceForTheModel() {
        var org = mock(andromeda.sei.ms.feature.organization.domain.Organization.class);
        var price = new andromeda.sei.ms.feature.organization.domain.AiPriceEntry("claude-sonnet-5-5", java.math.BigDecimal.ZERO, new java.math.BigDecimal("0.015"));
        when(org.getAiPrices()).thenReturn(List.of(price));
        when(organizations.findById(ORG)).thenReturn(Optional.of(org));

        useCase.execute(principal, batch(row("k1", 1, 2000)));

        assertThat(usages.byKey.get("k1").getCostUsd()).isEqualByComparingTo("0.030000");
    }

    private void withOpusPrices() {
        var org = mock(andromeda.sei.ms.feature.organization.domain.Organization.class);
        var price = new andromeda.sei.ms.feature.organization.domain.AiPriceEntry("claude-sonnet-5-5",
                new java.math.BigDecimal("0.005"), new java.math.BigDecimal("0.025"),
                new java.math.BigDecimal("0.0005"), new java.math.BigDecimal("0.00625"));
        when(org.getAiPrices()).thenReturn(List.of(price));
        when(organizations.findById(ORG)).thenReturn(Optional.of(org));
    }

    @Test
    void should_chargeCache_whenInsertingAnAgentRow() {
        withOpusPrices();

        useCase.execute(principal, batch(rowWith("k1", 1000, 2000, 1_000_000, 10_000)));

        assertThat(usages.byKey.get("k1").getCostUsd()).isEqualByComparingTo("0.617500");
    }

    @Test
    void should_chargeCache_whenAResendGrowsTheRow() {
        withOpusPrices();
        useCase.execute(principal, batch(rowWith("k1", 1000, 2000, 0, 0)));

        useCase.execute(principal, batch("8a3e0000-0000-4000-8000-000000000002",
                rowWith("k1", 1000, 2000, 1_000_000, 10_000)));

        assertThat(usages.byKey.get("k1").getCostUsd()).isEqualByComparingTo("0.617500");
    }

    // ---- helpers ----------------------------------------------------------------------------

    static String reason(UsageUploadResult r, String key) {
        return r.results().stream().filter(x -> key.equals(x.key())).findFirst().orElseThrow().reason();
    }

    static String reason(UsageUploadResult r, int index) {
        return r.results().get(index).reason();
    }

    static Boolean retryable(UsageUploadResult r, String key) {
        return r.results().stream().filter(x -> key.equals(x.key())).findFirst().orElseThrow().retryable();
    }

    static Boolean retryable(UsageUploadResult r, int index) {
        return r.results().get(index).retryable();
    }

    static UsageBatchInput batch(UsageBatchInput.Row... rows) {
        return batch(BATCH, rows);
    }

    static UsageBatchInput batch(String batchId, UsageBatchInput.Row... rows) {
        return new UsageBatchInput(batchId, "0.1.0", null, List.of(rows));
    }

    static UsageBatchInput.Row row(String key, long input, long output) {
        return rowWith(key, input, output, 0, 0);
    }

    static UsageBatchInput.Row rowWith(String key, long input, long output, long cacheRead, long cacheWrite) {
        return new UsageBatchInput.Row(key, "claude-code", "9f2c9f2c9f2c9f2c", "claude-sonnet-5-5",
                "2026-10-29T14:00:00Z", null, input, output, cacheRead, cacheWrite, 0L, 1L, "real",
                "github.com/acme/andromeda-ms", "feature/x", null, "none", Set.of());
    }

    static UsageBatchInput.Row withHour(UsageBatchInput.Row r, String hour) {
        return new UsageBatchInput.Row(r.key(), r.tool(), r.sessionHash(), r.model(), hour, r.day(), r.input(),
                r.output(), r.cacheRead(), r.cacheWrite(), r.thinking(), r.interactions(), r.origin(), r.repo(),
                r.branch(), r.taskRef(), r.attributionMethod(), r.malformed());
    }

    static UsageBatchInput.Row withDay(UsageBatchInput.Row r, String day) {
        return new UsageBatchInput.Row(r.key(), r.tool(), r.sessionHash(), r.model(), r.hour(), day, r.input(),
                r.output(), r.cacheRead(), r.cacheWrite(), r.thinking(), r.interactions(), r.origin(), r.repo(),
                r.branch(), r.taskRef(), r.attributionMethod(), r.malformed());
    }

    static UsageBatchInput.Row withOrigin(UsageBatchInput.Row r, String origin) {
        return new UsageBatchInput.Row(r.key(), r.tool(), r.sessionHash(), r.model(), r.hour(), r.day(), r.input(),
                r.output(), r.cacheRead(), r.cacheWrite(), r.thinking(), r.interactions(), origin, r.repo(),
                r.branch(), r.taskRef(), r.attributionMethod(), r.malformed());
    }

    static UsageBatchInput.Row withBranch(UsageBatchInput.Row r, String branch) {
        return new UsageBatchInput.Row(r.key(), r.tool(), r.sessionHash(), r.model(), r.hour(), r.day(), r.input(),
                r.output(), r.cacheRead(), r.cacheWrite(), r.thinking(), r.interactions(), r.origin(), r.repo(),
                branch, r.taskRef(), r.attributionMethod(), r.malformed());
    }

    static AIUsage storedRow(String key, long input, long output) {
        AIUsage u = new AIUsage("id-" + key, ORG, USER, null, "agent", LocalDate.of(2026, 10, 29), "claude-sonnet-5-5",
                "agent:x", "github.com/acme/andromeda-ms", DEVICE, "NOTE", input, output, 1, "claude-code", null, null,
                NOW, true, 0L, NOW, NOW);
        u.setAgentKey(key);
        u.setDeviceId(DEVICE);
        u.setBucketStart(Instant.parse("2026-10-29T14:00:00Z"));
        return u;
    }

    /** Behaves like the Mongo adapter: unique key per device, raise-to-max on update. */
    static class InMemoryUsages implements AgentAIUsageRepository {
        final Map<String, AIUsage> byKey = new LinkedHashMap<>();
        int inserts;
        int raises;
        private int seq;

        @Override
        public Map<String, AIUsage> findByKeys(String organizationId, String deviceId, Collection<String> keys) {
            Map<String, AIUsage> found = new HashMap<>();
            keys.forEach(k -> {
                if (byKey.containsKey(k)) found.put(k, byKey.get(k));
            });
            return found;
        }

        @Override
        public Optional<AIUsage> insert(AIUsage usage) {
            if (byKey.containsKey(usage.getAgentKey())) return Optional.empty();
            usage.setId("id-" + (++seq));
            byKey.put(usage.getAgentKey(), usage);
            inserts++;
            return Optional.of(usage);
        }

        @Override
        public void raiseToMax(String organizationId, String id, AIUsage values) {
            raises++;
            AIUsage stored = byKey.values().stream().filter(u -> id.equals(u.getId())).findFirst().orElseThrow();
            stored.setInputTokens(Math.max(stored.getInputTokens(), values.getInputTokens()));
            stored.setOutputTokens(Math.max(stored.getOutputTokens(), values.getOutputTokens()));
            stored.setCacheReadTokens(Math.max(stored.getCacheReadTokens(), values.getCacheReadTokens()));
            stored.setCacheWriteTokens(Math.max(stored.getCacheWriteTokens(), values.getCacheWriteTokens()));
            stored.setThinkingTokens(Math.max(stored.getThinkingTokens(), values.getThinkingTokens()));
            stored.setInteractions(Math.max(stored.getInteractions(), values.getInteractions()));
            stored.setCostUsd(values.getCostUsd());
        }

        @Override
        public List<AIUsage> findCountersByDeviceAndDayBetween(String o, String d, LocalDate from, LocalDate to) {
            return new ArrayList<>(byKey.values());
        }

        @Override
        public List<String> findKeysByDeviceAndDay(String o, String d, LocalDate day, int limit) {
            return byKey.values().stream().filter(u -> day.equals(u.getDay())).map(AIUsage::getAgentKey).sorted().toList();
        }
    }

    static class InMemoryBatches implements AgentUploadBatchRepository {
        final Map<String, AgentUploadBatch> stored = new HashMap<>();
        final Map<String, Instant> claims = new HashMap<>();
        /** Response the "other request" stores while this one waits (simulates the first request finishing). */
        UsageUploadResult completesWhileWaiting;
        int finds;
        int releases;

        @Override
        public Optional<AgentUploadBatch> find(String organizationId, String deviceId, String batchId) {
            finds++;
            String key = organizationId + deviceId + batchId;
            AgentUploadBatch b = stored.get(key);
            if (b != null && b.getResponse() == null && completesWhileWaiting != null) {
                b.setResponse(completesWhileWaiting);
            }
            return Optional.ofNullable(b);
        }

        @Override
        public Claim claim(String organizationId, String deviceId, String batchId, Instant now, Instant staleBefore,
                           Instant expiresAt) {
            String key = organizationId + deviceId + batchId;
            AgentUploadBatch b = stored.get(key);
            if (b == null || (b.getResponse() == null && claims.get(key).isBefore(staleBefore))) {
                stored.put(key, new AgentUploadBatch(organizationId, deviceId, batchId, null, now, expiresAt));
                claims.put(key, now);
                return new Claim.Owned(now);
            }
            return b.getResponse() != null ? new Claim.Done(b.getResponse()) : new Claim.Running();
        }

        @Override
        public void complete(String organizationId, String deviceId, String batchId, UsageUploadResult response) {
            AgentUploadBatch b = stored.get(organizationId + deviceId + batchId);
            if (b.getResponse() == null) {
                b.setResponse(response);
            }
        }

        @Override
        public void release(String organizationId, String deviceId, String batchId, Instant claimedAt) {
            String key = organizationId + deviceId + batchId;
            if (stored.get(key).getResponse() == null && claimedAt.equals(claims.get(key))) {
                stored.remove(key);
                claims.remove(key);
                releases++;
            }
        }

        /** Another request claimed the batch at {@code at} and is still processing it. */
        void runningSince(String batchId, Instant at) {
            String key = ORG + DEVICE + batchId;
            stored.put(key, new AgentUploadBatch(ORG, DEVICE, batchId, null, at, at.plus(Duration.ofDays(7))));
            claims.put(key, at);
        }
    }
}
