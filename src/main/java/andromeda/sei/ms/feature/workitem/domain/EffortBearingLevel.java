package andromeda.sei.ms.feature.workitem.domain;

/**
 * Which level of the work item hierarchy actually carries complexity/effort for an organization.
 *
 * <p>Teams model this in three different ways, and all three are legitimate:
 * <ol>
 *   <li>{@link #STORY} — both complexity and effort live on the User Story.</li>
 *   <li>{@link #STORY_AND_TASK} — complexity on the Story, effort on its child Tasks.</li>
 *   <li>{@link #TASK} — both live on the Tasks.</li>
 * </ol>
 *
 * <p>Before this existed, dashboards and AI allocation hard-coded "STORY or FEATURE" and ignored
 * TASK entirely, so models 2 and 3 reported zero velocity and offered no allocation targets even
 * though the data was there — it just lived one level down.
 *
 * <p>{@link #AUTO} is the default: rather than forcing every org through a config step, the
 * effective level is inferred from the synced data (see
 * {@code EffortBearingLevelResolver}). An org can still pin it explicitly when inference would be
 * ambiguous, e.g. during a migration between models.
 */
public enum EffortBearingLevel {
    AUTO,
    STORY,
    STORY_AND_TASK,
    TASK
}
