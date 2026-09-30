package andromeda.sei.ms.feature.azure.infrastructure.client;

import java.time.Instant;

/**
 * {@code GitPullRequestSearchCriteria} of one pull request listing (design D2).
 *
 * @param status        {@code active}, {@code completed}, {@code abandoned} or {@code all}
 * @param minTime       lower bound on the time range, or {@code null}
 * @param timeRangeType {@code created} or {@code closed}; ignored without {@code minTime}
 */
public record AzPullRequestQuery(String status, Instant minTime, String timeRangeType) {

    public static final String ACTIVE = "active";
    public static final String COMPLETED = "completed";
    public static final String ABANDONED = "abandoned";
    public static final String ALL = "all";
    public static final String CREATED = "created";
    public static final String CLOSED = "closed";

    public static AzPullRequestQuery active() {
        return new AzPullRequestQuery(ACTIVE, null, null);
    }

    public static AzPullRequestQuery closedSince(String status, Instant minTime) {
        return new AzPullRequestQuery(status, minTime, CLOSED);
    }

    public static AzPullRequestQuery createdSince(Instant minTime) {
        return new AzPullRequestQuery(ALL, minTime, CREATED);
    }
}
