package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.AllocationApproverAccess;
import andromeda.sei.ms.feature.allocation.application.dto.ApprovalQueueItemOutput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static andromeda.sei.ms.feature.allocation.application.usecase.ApprovalQueueTestData.CODE;
import static andromeda.sei.ms.feature.allocation.application.usecase.ApprovalQueueTestData.ORG;
import static andromeda.sei.ms.feature.allocation.application.usecase.ApprovalQueueTestData.awaiting;
import static andromeda.sei.ms.feature.allocation.application.usecase.ApprovalQueueTestData.caller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetApprovalQueueUseCaseTest {

    private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");

    @Mock AiUsageAllocationRepository allocationRepository;
    @Mock AIUsageRepository aiUsageRepository;
    @Mock UserRepository userRepository;
    @Mock EffectivePermissionService effectivePermissionService;

    private GetApprovalQueueUseCase useCase() {
        return new GetApprovalQueueUseCase(allocationRepository, aiUsageRepository, userRepository,
                new AllocationApproverAccess(effectivePermissionService));
    }

    private void callerApprovesOn(UserPermissionSnapshot caller, String... projectIds) {
        when(effectivePermissionService.projectIdsWith(caller, CODE)).thenReturn(Set.of(projectIds));
    }

    private static AIUsage usage(String id, String repository, String branch, String cost, long in, long out) {
        AIUsage u = org.mockito.Mockito.mock(AIUsage.class);
        lenient().when(u.getId()).thenReturn(id);
        lenient().when(u.getRepository()).thenReturn(repository);
        lenient().when(u.getBranch()).thenReturn(branch);
        lenient().when(u.getCostUsd()).thenReturn(new BigDecimal(cost));
        lenient().when(u.getInputTokens()).thenReturn(in);
        lenient().when(u.getOutputTokens()).thenReturn(out);
        return u;
    }

    @Test
    void listsOnlyWhatCrossesTheApproverProjects_withoutTheOwnDay_oldestFirst() {
        UserPermissionSnapshot caller = caller("lead", false);
        callerApprovesOn(caller, "p-1");
        AiUsageAllocation older = awaiting("a-old", "ana", List.of("p-1"), T0);
        AiUsageAllocation newer = awaiting("a-new", "bia", List.of("p-1", "p-9"), T0.plusSeconds(60));
        AiUsageAllocation otherProject = awaiting("a-other", "ana", List.of("p-2"), T0);
        AiUsageAllocation own = awaiting("a-own", "lead", List.of("p-1"), T0);
        AiUsageAllocation orgOnly = awaiting("a-orgonly", "ana", List.of(), T0);
        when(allocationRepository.findAwaitingApprovalByOrganizationId(ORG))
                .thenReturn(List.of(newer, otherProject, own, orgOnly, older));
        AIUsage uOld = usage("u-a-old", "org/repo", "feature/x", "1.50", 100, 50);
        AIUsage uNew = usage("u-a-new", "org/other", null, "2.00", 10, 5);
        when(aiUsageRepository.findByOrganizationIdAndIdIn(any(), any())).thenReturn(List.of(uOld, uNew));
        when(userRepository.findByIdAndOrganizationId(any(), any())).thenAnswer(inv -> {
            User u = org.mockito.Mockito.mock(User.class);
            when(u.getName()).thenReturn("Nome de " + inv.getArgument(0));
            return Optional.of(u);
        });

        List<ApprovalQueueItemOutput> queue = useCase().execute(caller);

        assertThat(queue).extracting(ApprovalQueueItemOutput::allocationId).containsExactly("a-old", "a-new");
        ApprovalQueueItemOutput first = queue.get(0);
        assertThat(first.userName()).isEqualTo("Nome de ana");
        assertThat(first.costUsd()).isEqualByComparingTo("1.50");
        assertThat(first.tokens()).isEqualTo(150);
        assertThat(first.repositories()).containsExactly(new ApprovalQueueItemOutput.Repository("org/repo", "feature/x"));
        assertThat(first.reasons()).hasSize(1);
        assertThat(first.requestedAt()).isEqualTo(T0);
        assertThat(first.splits()).hasSize(1);
        assertThat(queue.get(1).repositories()).containsExactly(new ApprovalQueueItemOutput.Repository("org/other", null));
    }

    @Test
    void anAllocationWithoutApproverProjectsIsVisibleOnlyToTheOrganizationLevelHolder() {
        UserPermissionSnapshot orgLead = caller("lead", true);
        callerApprovesOn(orgLead);
        AiUsageAllocation orgOnly = awaiting("a-orgonly", "ana", List.of(), T0);
        when(allocationRepository.findAwaitingApprovalByOrganizationId(ORG)).thenReturn(List.of(orgOnly));
        when(aiUsageRepository.findByOrganizationIdAndIdIn(any(), any())).thenReturn(List.of());
        when(userRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.empty());

        List<ApprovalQueueItemOutput> queue = useCase().execute(orgLead);

        assertThat(queue).extracting(ApprovalQueueItemOutput::allocationId).containsExactly("a-orgonly");
        assertThat(queue.get(0).userName()).isNull();
    }

    @Test
    void anotherOrganizationNeverReachesTheQueue_theRepositoryIsAskedForTheCallerOrganization() {
        UserPermissionSnapshot caller = caller("lead", true);
        lenient().when(effectivePermissionService.projectIdsWith(any(), any())).thenReturn(Set.of());
        when(allocationRepository.findAwaitingApprovalByOrganizationId(ORG)).thenReturn(List.of());

        assertThat(useCase().execute(caller)).isEmpty();
    }
}
