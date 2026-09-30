package andromeda.sei.ms.feature.github.presentation;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Legacy GitHub routes answering {@code 308 Permanent Redirect} to their {@code /api/v1/repos}
 * equivalents for one release (vcs-repository-read-api). 308 keeps the method and the body, so an
 * old client's {@code POST} of a binding replays as-is ({@code repoId} is accepted as an alias of
 * {@code fullName}). The query string is preserved. The target endpoint enforces the permission.
 *
 * <p>Only the exact legacy patterns are mapped: the GitHub connection endpoints under
 * {@code /api/v1/integrations/github} (status, sync, repair, data, repos/available) stay live.
 */
@Hidden
@RestController
public class LegacyGithubRoutesController {

    private static final String REPOS = "/api/v1/repos";

    @RequestMapping(value = "/api/v1/github/projects/{projectId}/repos", method = RequestMethod.GET)
    public ResponseEntity<Void> projectRepos(@PathVariable String projectId, HttpServletRequest request) {
        return redirect(request, REPOS + "/projects/" + projectId + "/repositories");
    }

    @RequestMapping(value = "/api/v1/github/repos/{repoId}/pull-requests", method = RequestMethod.GET)
    public ResponseEntity<Void> pullRequests(@PathVariable String repoId, HttpServletRequest request) {
        return redirect(request, REPOS + "/repositories/" + repoId + "/pull-requests");
    }

    @RequestMapping(value = "/api/v1/github/repos/{repoId}/metrics", method = RequestMethod.GET)
    public ResponseEntity<Void> metrics(@PathVariable String repoId, HttpServletRequest request) {
        return redirect(request, REPOS + "/repositories/" + repoId + "/delivery-metrics");
    }

    @RequestMapping(value = "/api/v1/github/repos/{repoId}/commit-activity", method = RequestMethod.GET)
    public ResponseEntity<Void> commitActivity(@PathVariable String repoId, HttpServletRequest request) {
        return redirect(request, REPOS + "/repositories/" + repoId + "/commit-activity");
    }

    @RequestMapping(value = "/api/v1/github/repos/{repoId}/pull-requests/{pullRequestId}/commits",
            method = RequestMethod.GET)
    public ResponseEntity<Void> pullRequestCommits(@PathVariable String repoId, @PathVariable String pullRequestId,
                                                   HttpServletRequest request) {
        return redirect(request, REPOS + "/repositories/" + repoId + "/pull-requests/" + pullRequestId + "/commits");
    }

    @RequestMapping(value = "/api/v1/integrations/github/projects/{projectId}/bindings",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> projectBindings(@PathVariable String projectId, HttpServletRequest request) {
        return redirect(request, REPOS + "/projects/" + projectId + "/bindings");
    }

    @RequestMapping(value = "/api/v1/integrations/github/projects/{projectId}/bindings/{bindingId}",
            method = RequestMethod.DELETE)
    public ResponseEntity<Void> unbind(@PathVariable String projectId, @PathVariable String bindingId,
                                       HttpServletRequest request) {
        return redirect(request, REPOS + "/projects/" + projectId + "/bindings/" + bindingId);
    }

    @RequestMapping(value = "/api/v1/integrations/github/bindings", method = RequestMethod.GET)
    public ResponseEntity<Void> bindings(HttpServletRequest request) {
        return redirect(request, REPOS + "/bindings");
    }

    @RequestMapping(value = "/api/v1/integrations/github/bindings/{bindingId}", method = RequestMethod.PATCH)
    public ResponseEntity<Void> toggle(@PathVariable String bindingId, HttpServletRequest request) {
        return redirect(request, REPOS + "/bindings/" + bindingId);
    }

    @RequestMapping(value = "/api/v1/integrations/github/work-item-link-config",
            method = {RequestMethod.GET, RequestMethod.PUT})
    public ResponseEntity<Void> workItemLinkConfig(HttpServletRequest request) {
        return redirect(request, REPOS + "/settings/work-item-link");
    }

    static ResponseEntity<Void> redirect(HttpServletRequest request, String path) {
        String query = request.getQueryString();
        String target = request.getContextPath() + path + (query == null || query.isBlank() ? "" : "?" + query);
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT).location(URI.create(target)).build();
    }
}
