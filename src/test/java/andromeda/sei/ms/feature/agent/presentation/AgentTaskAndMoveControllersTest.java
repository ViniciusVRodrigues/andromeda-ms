package andromeda.sei.ms.feature.agent.presentation;

import andromeda.sei.ms.feature.agent.application.usage.ClearActiveTaskUseCase;
import andromeda.sei.ms.feature.agent.application.usage.GetAgentTodayUseCase;
import andromeda.sei.ms.feature.agent.application.usage.MoveAgentUsageUseCase;
import andromeda.sei.ms.feature.agent.application.usage.SetActiveTaskUseCase;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRequestInvalidException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTaskNotFoundException;
import andromeda.sei.ms.feature.agent.domain.exception.AiAllocationNotAgentException;
import andromeda.sei.ms.feature.agent.domain.exception.AiAllocationNotFoundException;
import andromeda.sei.ms.feature.agent.domain.exception.AiUsageNotInAllocationException;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentTaskAndMoveControllersTest {

    static final AgentPrincipal PRINCIPAL = new AgentPrincipal("org-1", "user-1", "dev-1");

    SetActiveTaskUseCase setActive = mock(SetActiveTaskUseCase.class);
    ClearActiveTaskUseCase clear = mock(ClearActiveTaskUseCase.class);
    GetAgentTodayUseCase today = mock(GetAgentTodayUseCase.class);
    MoveAgentUsageUseCase move = mock(MoveAgentUsageUseCase.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    MockMvc build(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new AgentExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    MockMvc agentMvc() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(PRINCIPAL, "n"));
        return build(new AgentActiveTaskController(setActive, clear));
    }

    MockMvc personMvc(Object controller) {
        AndromedaUserDetails user = mock(AndromedaUserDetails.class);
        when(user.getOrganizationId()).thenReturn("org-1");
        when(user.getUserId()).thenReturn("user-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        return build(controller);
    }

    // ---- PUT/DELETE /agent/v1/active-task -----------------------------------------------------

    @Test
    void should_answerTheTaskAndTheRetroactiveRows_when_theActiveTaskIsSet() throws Exception {
        when(setActive.execute(eq(PRINCIPAL), any())).thenReturn(
                new SetActiveTaskUseCase.Output("wi-1", "AB#123", "Task 123", 4));

        agentMvc().perform(put("/agent/v1/active-task").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskRef\":\"AB#123\",\"day\":\"2026-10-29\",\"repo\":\"github.com/a/b\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workItemId").value("wi-1"))
                .andExpect(jsonPath("$.retroactiveRows").value(4));
        verify(setActive).execute(eq(PRINCIPAL), eq(new SetActiveTaskUseCase.Input("AB#123", null,
                LocalDate.of(2026, 10, 29), "github.com/a/b")));
    }

    @Test
    void should_answer400_when_theBodyIsInvalid() throws Exception {
        agentMvc().perform(put("/agent/v1/active-task").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskRef\":\"\",\"day\":\"2026-10-29\"}"))
                .andExpect(status().isBadRequest());
        agentMvc().perform(put("/agent/v1/active-task").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskRef\":\"AB#1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_answer400_when_theDayIsOutOfTheWindow() throws Exception {
        when(setActive.execute(eq(PRINCIPAL), any())).thenThrow(new AgentRequestInvalidException("day"));

        agentMvc().perform(put("/agent/v1/active-task").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskRef\":\"AB#1\",\"day\":\"2026-10-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_answer422_when_theTaskIsNotFound() throws Exception {
        when(setActive.execute(eq(PRINCIPAL), any())).thenThrow(new AgentTaskNotFoundException());

        agentMvc().perform(put("/agent/v1/active-task").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskRef\":\"AB#1\",\"day\":\"2026-10-29\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AGENT_TASK_NOT_FOUND"));
    }

    @Test
    void should_answer204_when_theActiveTaskIsCleared() throws Exception {
        agentMvc().perform(delete("/agent/v1/active-task")).andExpect(status().isNoContent());

        verify(clear).execute(PRINCIPAL);
    }

    // ---- GET /api/v1/agent/today -----------------------------------------------------------------

    @Test
    void should_answerTodayOfTheLoggedPerson() throws Exception {
        when(today.execute("org-1", "user-1")).thenReturn(new GetAgentTodayUseCase.Today(
                List.of(new GetAgentTodayUseCase.ResolvedGroup("github.com/a/b", "main", true,
                        new GetAgentTodayUseCase.WorkItemRef("wi-1", "AB#123", "Task"), "BRANCH",
                        Instant.parse("2026-10-29T12:00:00Z"), "dev-1")),
                List.of(new GetAgentTodayUseCase.UnassignedGroup("github.com/a/c", 2, 15, "dev-1")),
                List.of()));

        personMvc(new AgentTodayController(today)).perform(get("/api/v1/agent/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved[0].branchApproximate").value(true))
                .andExpect(jsonPath("$.resolved[0].workItem.displayId").value("AB#123"))
                .andExpect(jsonPath("$.unassigned[0].rows").value(2));
    }

    // ---- POST /api/v1/ai/allocations/{id}/move -----------------------------------------------------

    MockMvc moveMvc() {
        return personMvc(new AgentAllocationMoveController(move));
    }

    @Test
    void should_answerTheAllocations_when_theRowsAreMoved() throws Exception {
        MoveAgentUsageUseCase.AllocationView target = new MoveAgentUsageUseCase.AllocationView("a2",
                LocalDate.of(2026, 10, 29), AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL,
                true, true, List.of("u1"), List.of(), null);
        when(move.execute("org-1", "user-1", "a1", List.of("u1"), "wi-200"))
                .thenReturn(new MoveAgentUsageUseCase.MoveResult(null, target));

        moveMvc().perform(post("/api/v1/ai/allocations/a1/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiUsageIds\":[\"u1\"],\"workItemId\":\"wi-200\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.assignedBy").value("MANUAL"))
                .andExpect(jsonPath("$.target.movedFromAgent").value(true));
    }

    @Test
    void should_answer400_when_theMoveBodyIsInvalid() throws Exception {
        moveMvc().perform(post("/api/v1/ai/allocations/a1/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiUsageIds\":[],\"workItemId\":\"wi-200\"}"))
                .andExpect(status().isBadRequest());
        moveMvc().perform(post("/api/v1/ai/allocations/a1/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aiUsageIds\":[\"u1\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_mapTheFourMoveErrors() throws Exception {
        String body = "{\"aiUsageIds\":[\"u1\"],\"workItemId\":\"wi-200\"}";
        when(move.execute(any(), any(), eq("nf"), any(), any())).thenThrow(new AiAllocationNotFoundException());
        when(move.execute(any(), any(), eq("na"), any(), any())).thenThrow(new AiAllocationNotAgentException());
        when(move.execute(any(), any(), eq("ni"), any(), any())).thenThrow(new AiUsageNotInAllocationException());
        when(move.execute(any(), any(), eq("nt"), any(), any())).thenThrow(new AgentTaskNotFoundException());
        MockMvc mvc = moveMvc();

        mvc.perform(post("/api/v1/ai/allocations/nf/move").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AI_ALLOCATION_NOT_FOUND"));
        mvc.perform(post("/api/v1/ai/allocations/na/move").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("AI_ALLOCATION_NOT_AGENT"));
        mvc.perform(post("/api/v1/ai/allocations/ni/move").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("AI_USAGE_NOT_IN_ALLOCATION"));
        mvc.perform(post("/api/v1/ai/allocations/nt/move").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("AGENT_TASK_NOT_FOUND"));
    }
}
