package andromeda.sei.ms.feature.agent.application.attribution;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The device active task. It applies to a row only on the day it was marked ({@code day}, local), at or after
 * {@code since}, in the same repository and on the same branch (each checked only when both sides have it).
 */
public record ActiveTaskHint(String taskRef, Instant since, String repository, String branch, LocalDate day) {

    public ActiveTaskHint(String taskRef, Instant since, String repository) {
        this(taskRef, since, repository, null, null);
    }
}
