package andromeda.sei.ms.feature.azure.infrastructure.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 3.5: the Git client against a real HTTP server replaying the official 7.1 samples
 * ({@code src/test/resources/fixtures/azure-repos}). Checks the exact query parameters sent.
 */
class AzureReposApiClientTest {

    private static final String AUTH = "Basic OnBhdA==";
    private static final String PROJECT = "a7573007-bbb3-4341-b726-0c4148a07853";
    private static final String REPO = "3411ebc1-d5aa-464f-9615-0b527bc66719";

    private FakeAzureDevOpsServer server;
    private AzureReposApiClient client;
    private String orgUrl;

    @BeforeEach
    void setUp() {
        server = new FakeAzureDevOpsServer();
        client = new AzureReposApiClient(WebClient.builder().build());
        orgUrl = server.orgUrl("fabrikam");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void shouldListOrganizationRepositoriesWithTheirProjectAndDefaultBranch() {
        server.enqueueFixture("repositories.json");

        List<AzRepositoryDto> repositories = client.listRepositories(orgUrl + "/", AUTH);

        assertThat(repositories).extracting(AzRepositoryDto::name)
                .containsExactly("AnotherRepository", "Fabrikam-Fiber-Git", "TestGit");
        assertThat(repositories.getFirst().defaultBranch()).isNull();
        assertThat(repositories.get(1).defaultBranch()).isEqualTo("refs/heads/master");
        assertThat(repositories.get(2).project().id()).isEqualTo("281f9a5b-af0d-49b4-a1df-fe6f5e5f84d0");
        assertThat(repositories.getFirst().isDisabled()).isFalse();
        assertThat(server.requests()).singleElement().satisfies(r -> {
            assertThat(r.rawPathAndQuery()).isEqualTo("/fabrikam/_apis/git/repositories?api-version=7.1");
            assertThat(r.authorization()).isEqualTo(AUTH);
        });
    }

    @Test
    void shouldGetOneRepositoryUnderItsProject() {
        server.enqueue(200, """
                {"id":"%s","name":"2016_10_31","defaultBranch":"refs/heads/main","isDisabled":true,
                 "project":{"id":"%s","name":"2016_10_31"}}
                """.formatted(REPO, PROJECT));

        AzRepositoryDto repository = client.getRepository(orgUrl, PROJECT, REPO, AUTH);

        assertThat(repository.isDisabled()).isTrue();
        assertThat(server.requests().getFirst().rawPathAndQuery())
                .isEqualTo("/fabrikam/" + PROJECT + "/_apis/git/repositories/" + REPO + "?api-version=7.1");
    }

    @Test
    void shouldListActivePullRequestsWithTopAndSkip() {
        server.enqueueFixture("pullrequests-active.json");

        List<AzPullRequestDto> pullRequests = client.listPullRequests(orgUrl, PROJECT, REPO,
                AzPullRequestQuery.active(), AUTH);

        assertThat(pullRequests).extracting(AzPullRequestDto::pullRequestId).containsExactly(22, 21, 1);
        AzPullRequestDto first = pullRequests.getFirst();
        assertThat(first.createdBy().id()).isEqualTo("d6245f20-2af8-44f4-9451-8107cb2767db");
        assertThat(first.createdBy().uniqueName()).isEqualTo("fabrikamfiber16@hotmail.com");
        assertThat(first.creationDate()).isEqualTo("2016-11-01T16:30:31.6655471Z");
        assertThat(first.draft()).isNull();
        assertThat(server.requests().getFirst().rawPathAndQuery()).isEqualTo("/fabrikam/" + PROJECT
                + "/_apis/git/repositories/" + REPO
                + "/pullrequests?searchCriteria.status=active&$top=100&$skip=0&api-version=7.1");
    }

    @Test
    void shouldQueryClosedPullRequestsByClosedTimeRange() {
        server.enqueueFixture("pullrequests-completed.json");

        List<AzPullRequestDto> pullRequests = client.listPullRequests(orgUrl, PROJECT, REPO,
                AzPullRequestQuery.closedSince(AzPullRequestQuery.COMPLETED, Instant.parse("2026-09-29T10:15:30Z")),
                AUTH);

        assertThat(pullRequests).singleElement().satisfies(pr -> {
            assertThat(pr.status()).isEqualTo("completed");
            assertThat(pr.closedDate()).isEqualTo("2016-11-01T16:30:02.0413914Z");
        });
        assertThat(server.requests().getFirst().rawPathAndQuery()).contains(
                "searchCriteria.status=completed&searchCriteria.minTime=2026-09-29T10:15:30Z"
                        + "&searchCriteria.queryTimeRangeType=closed&$top=100&$skip=0");
    }

    @Test
    void shouldPageUntilAShortPage() {
        server.enqueue(200, pullRequestPage(AzureReposApiClient.PAGE_SIZE, 1000))
                .enqueue(200, pullRequestPage(1, 5000));

        List<AzPullRequestDto> pullRequests = client.listPullRequests(orgUrl, PROJECT, REPO,
                AzPullRequestQuery.createdSince(Instant.parse("2026-01-01T00:00:00Z")), AUTH);

        assertThat(pullRequests).hasSize(AzureReposApiClient.PAGE_SIZE + 1);
        assertThat(server.requests()).extracting(FakeAzureDevOpsServer.Recorded::rawPathAndQuery)
                .satisfiesExactly(
                        first -> assertThat(first).contains("searchCriteria.status=all")
                                .contains("searchCriteria.queryTimeRangeType=created").contains("$skip=0"),
                        second -> assertThat(second).contains("$skip=100"));
    }

    @Test
    void shouldReadThreadsWithTheirTypedPropertyBag() {
        server.enqueueFixture("threads.json");

        List<AzThreadDto> threads = client.listThreads(orgUrl, PROJECT, REPO, 22, AUTH);

        assertThat(threads).hasSize(8);
        AzThreadDto vote = threads.get(2);
        assertThat(vote.property("CodeReviewThreadType")).isEqualTo("VoteUpdate");
        assertThat(vote.property("CodeReviewVoteResult")).isEqualTo("10");
        assertThat(threads.get(1).property("CodeReviewReviewersUpdatedNumAdded")).isEqualTo("1");
        assertThat(threads.getFirst().comments().getFirst().author().isContainer()).isTrue();
        assertThat(threads.getLast().comments().get(1).isDeleted()).isTrue();
        assertThat(server.requests().getFirst().rawPathAndQuery()).isEqualTo("/fabrikam/" + PROJECT
                + "/_apis/git/repositories/" + REPO + "/pullRequests/22/threads?api-version=7.1");
    }

    @Test
    void shouldListPullRequestWorkItems() {
        server.enqueueFixture("pr-workitems.json");

        assertThat(client.listPullRequestWorkItems(orgUrl, PROJECT, REPO, 22, AUTH))
                .extracting(AzWorkItemRefDto::id).containsExactly("1234", "1240");
        assertThat(server.requests().getFirst().rawPathAndQuery()).endsWith("/pullRequests/22/workitems?api-version=7.1");
    }

    @Test
    void shouldFollowTheContinuationTokenForPullRequestCommits() {
        server.enqueue(200, FakeAzureDevOpsServer.fixture("pr-commits.json"), Map.of("x-ms-continuationtoken", "abc"))
                .enqueue(200, "{\"count\":0,\"value\":[]}");

        List<AzCommitDto> commits = client.listPullRequestCommits(orgUrl, PROJECT, REPO, 22, AUTH);

        assertThat(commits).extracting(AzCommitDto::commitId)
                .containsExactly("b60280bc6e62e2f880f1b63c1e24987664d3bda3", "8c9396b5cf22f929767c7172e9dbbe777ddc6357");
        assertThat(commits.getFirst().author().email()).isEqualTo("fabrikamfiber16@hotmail.com");
        assertThat(server.requests()).extracting(FakeAzureDevOpsServer.Recorded::rawPathAndQuery).satisfiesExactly(
                first -> assertThat(first).endsWith("/pullRequests/22/commits?$top=100&api-version=7.1"),
                second -> assertThat(second).contains("continuationToken=abc"));
    }

    @Test
    void shouldListBranchCommitsSinceADate() {
        server.enqueueFixture("commits-branch.json");

        List<AzCommitDto> commits = client.listCommits(orgUrl, PROJECT, REPO, "refs/heads/main",
                Instant.parse("2026-09-29T00:00:00Z"), AUTH);

        assertThat(commits).hasSize(3);
        assertThat(commits.getFirst().author().date()).isEqualTo("2018-06-15T17:06:53Z");
        assertThat(server.requests().getFirst().rawPathAndQuery()).isEqualTo("/fabrikam/" + PROJECT
                + "/_apis/git/repositories/" + REPO + "/commits?searchCriteria.itemVersion.version=main"
                + "&searchCriteria.itemVersion.versionType=branch&searchCriteria.fromDate=2026-09-29T00:00:00Z"
                + "&searchCriteria.$top=100&searchCriteria.$skip=0&api-version=7.1");
    }

    @Test
    void shouldEncodeBranchNamesWithSlashesAndSpaces() {
        server.enqueue(200, "{\"count\":0,\"value\":[]}");

        client.listCommits(orgUrl, PROJECT, REPO, "refs/heads/release/v 1", null, AUTH);

        assertThat(server.requests().getFirst().rawPathAndQuery())
                .contains("searchCriteria.itemVersion.version=release/v%201")
                .doesNotContain("fromDate");
    }

    @Test
    void shouldSurfaceUnauthorizedWithoutRetrying() {
        server.enqueue(401, "{}");

        assertThatThrownBy(() -> client.listRepositories(orgUrl, AUTH))
                .isInstanceOf(WebClientResponseException.Unauthorized.class);
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void shouldRetryThrottledRequestsHonoringRetryAfter() {
        server.enqueue(429, "{}", Map.of("Retry-After", "0")).enqueueFixture("repositories.json");

        assertThat(client.listRepositories(orgUrl, AUTH)).hasSize(3);
        assertThat(server.requests()).hasSize(2);
    }

    private static String pullRequestPage(int size, int firstId) {
        String items = IntStream.range(0, size)
                .mapToObj(i -> "{\"pullRequestId\":" + (firstId + i) + ",\"status\":\"completed\"}")
                .collect(Collectors.joining(","));
        return "{\"count\":" + size + ",\"value\":[" + items + "]}";
    }
}
