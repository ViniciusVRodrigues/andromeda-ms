package andromeda.sei.ms.feature.github.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** vcs-repository-read-api "Legacy GitHub read routes redirect": 308 to the /api/v1/repos equivalent. */
class LegacyGithubRoutesControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LegacyGithubRoutesController()).build();
    }

    @Test
    void should_redirectProjectRepos() throws Exception {
        mockMvc.perform(get("/api/v1/github/projects/p1/repos"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(header().string("Location", "/api/v1/repos/projects/p1/repositories"));
    }

    @Test
    void should_redirectRepositoryReads_keepingTheQueryString() throws Exception {
        mockMvc.perform(get("/api/v1/github/repos/r1/pull-requests?status=OPEN&page=2"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(header().string("Location", "/api/v1/repos/repositories/r1/pull-requests?status=OPEN&page=2"));
        mockMvc.perform(get("/api/v1/github/repos/r1/metrics?startDate=2026-07-01"))
                .andExpect(header().string("Location", "/api/v1/repos/repositories/r1/delivery-metrics?startDate=2026-07-01"));
        mockMvc.perform(get("/api/v1/github/repos/r1/commit-activity"))
                .andExpect(header().string("Location", "/api/v1/repos/repositories/r1/commit-activity"));
        mockMvc.perform(get("/api/v1/github/repos/r1/pull-requests/pr1/commits"))
                .andExpect(header().string("Location", "/api/v1/repos/repositories/r1/pull-requests/pr1/commits"));
    }

    @Test
    void should_redirectBindingRoutes_withTheirMethod() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/github/projects/p1/bindings"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(header().string("Location", "/api/v1/repos/projects/p1/bindings"));
        mockMvc.perform(get("/api/v1/integrations/github/projects/p1/bindings"))
                .andExpect(header().string("Location", "/api/v1/repos/projects/p1/bindings"));
        mockMvc.perform(delete("/api/v1/integrations/github/projects/p1/bindings/b1"))
                .andExpect(header().string("Location", "/api/v1/repos/projects/p1/bindings/b1"));
        mockMvc.perform(get("/api/v1/integrations/github/bindings"))
                .andExpect(header().string("Location", "/api/v1/repos/bindings"));
        mockMvc.perform(patch("/api/v1/integrations/github/bindings/b1"))
                .andExpect(status().isPermanentRedirect())
                .andExpect(header().string("Location", "/api/v1/repos/bindings/b1"));
    }

    @Test
    void should_redirectWorkItemLinkConfig_toOrganizationSetting() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/github/work-item-link-config"))
                .andExpect(header().string("Location", "/api/v1/repos/settings/work-item-link"));
        mockMvc.perform(put("/api/v1/integrations/github/work-item-link-config"))
                .andExpect(status().isPermanentRedirect());
    }
}
