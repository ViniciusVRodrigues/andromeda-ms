package andromeda.sei.ms.feature.organization.infrastructure.scheduling;

import andromeda.sei.ms.feature.agent.application.event.FirstAgentDeviceApproved;
import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase.Trigger;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkItemPatternDetectionJobTest {

    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final VcsRepoBindingRepository bindings = mock(VcsRepoBindingRepository.class);
    private final DetectWorkItemPatternsUseCase detect = mock(DetectWorkItemPatternsUseCase.class);

    private Organization org(String id, WorkItemPatterns.Mode mode) {
        Organization o = new Organization(id, id, id, null, null, List.of(), null, null, true, 1L,
                Instant.now(), Instant.now());
        o.setWorkItemPatterns(new WorkItemPatterns(true, mode, null, null, null, null, null, null));
        return o;
    }

    @Test
    void should_runForDefaultAndDetectedWithABinding_andSkipCustomAndUnlinked() {
        when(organizations.findAllActive()).thenReturn(List.of(org("a", WorkItemPatterns.Mode.DEFAULT),
                org("b", WorkItemPatterns.Mode.DETECTED), org("c", WorkItemPatterns.Mode.CUSTOM),
                org("d", WorkItemPatterns.Mode.DEFAULT)));
        when(bindings.findAllActiveByOrganizationId(anyString())).thenReturn(List.of());
        when(bindings.findAllActiveByOrganizationId("a")).thenReturn(List.of(mock(VcsRepoBinding.class)));
        when(bindings.findAllActiveByOrganizationId("b")).thenReturn(List.of(mock(VcsRepoBinding.class)));

        new WorkItemPatternDetectionJob(organizations, bindings, detect).run();

        verify(detect).execute("a", Trigger.SCHEDULED);
        verify(detect).execute("b", Trigger.SCHEDULED);
        verify(detect, never()).execute("c", Trigger.SCHEDULED);
        verify(detect, never()).execute("d", Trigger.SCHEDULED);
    }

    @Test
    void should_goOn_when_oneOrganizationFails() {
        when(organizations.findAllActive()).thenReturn(List.of(org("a", WorkItemPatterns.Mode.DEFAULT),
                org("b", WorkItemPatterns.Mode.DEFAULT)));
        when(bindings.findAllActiveByOrganizationId(anyString())).thenReturn(List.of(mock(VcsRepoBinding.class)));
        doThrow(new IllegalStateException("boom")).when(detect).execute("a", Trigger.SCHEDULED);

        new WorkItemPatternDetectionJob(organizations, bindings, detect).run();

        verify(detect).execute("b", Trigger.SCHEDULED);
    }

    @Test
    void should_swallowTheFailure_inTheFirstDeviceListener() {
        doThrow(new IllegalStateException("boom")).when(detect).execute("a", Trigger.FIRST_AGENT_DEVICE);

        new FirstAgentDeviceDetectionListener(detect).on(new FirstAgentDeviceApproved("a"));

        verify(detect).execute("a", Trigger.FIRST_AGENT_DEVICE);
    }
}
