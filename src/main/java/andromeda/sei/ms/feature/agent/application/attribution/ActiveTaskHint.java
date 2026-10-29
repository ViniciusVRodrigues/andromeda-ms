package andromeda.sei.ms.feature.agent.application.attribution;

import java.time.Instant;

/** The device active task: applies to rows with bucketStart at or after since (and the same repository, when set). */
public record ActiveTaskHint(String taskRef, Instant since, String repository) {}
