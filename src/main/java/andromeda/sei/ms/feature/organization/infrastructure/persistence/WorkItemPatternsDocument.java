package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Subdocumento embutido em {@code organizations.workItemPatterns}; ausente = nunca configurado. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkItemPatternsDocument {

    private boolean enabled;
    /** DEFAULT | DETECTED | CUSTOM. */
    private String mode;
    private List<EntryDocument> patterns;
    private String confirmedBy;
    private Instant confirmedAt;
    private String updatedBy;
    private Instant updatedAt;
    private DetectionDocument detection;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntryDocument {
        private String regex;
        private String preset;
        private boolean alsoInText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectionDocument {
        private Instant ranAt;
        private int branchesScanned;
        private double coverage;
        private boolean applied;
        private List<CandidateDocument> candidates;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateDocument {
        private String regex;
        private String preset;
        private double coverage;
        private double precision;
        private int matchedBranches;
        private int resolvedBranches;
        private List<ExampleDocument> examples;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExampleDocument {
        private String branch;
        private String workItemNumber;
        private String title;
    }
}
