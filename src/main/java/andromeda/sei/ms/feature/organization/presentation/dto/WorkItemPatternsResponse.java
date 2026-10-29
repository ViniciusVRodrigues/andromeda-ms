package andromeda.sei.ms.feature.organization.presentation.dto;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;

import java.time.Instant;
import java.util.List;

/** Design 14.4. {@code detection} is always present (null until a detection ran). */
public record WorkItemPatternsResponse(boolean enabled, String mode, List<PatternResponse> patterns,
                                       List<PatternResponse> defaults, List<PresetResponse> presets,
                                       String confirmedBy, Instant confirmedAt, String updatedBy, Instant updatedAt,
                                       DetectionResponse detection) {

    public record PatternResponse(String regex, String preset, boolean alsoInText) {}

    public record PresetResponse(String id, String regex) {}

    public record DetectionResponse(Instant ranAt, int branchesScanned, double coverage, boolean applied,
                                    List<CandidateResponse> candidates) {}

    public record CandidateResponse(String regex, String preset, double coverage, double precision,
                                    int matchedBranches, int resolvedBranches, List<ExampleResponse> examples) {}

    public record ExampleResponse(String branch, String workItemNumber, String title) {}

    public static WorkItemPatternsResponse from(WorkItemPatterns p) {
        WorkItemPatterns.Detection d = p.detection();
        DetectionResponse detection = d == null ? null : new DetectionResponse(d.ranAt(), d.branchesScanned(),
                d.coverage(), d.applied(), d.candidates().stream().map(c -> new CandidateResponse(c.regex(), c.preset(),
                c.coverage(), c.precision(), c.matchedBranches(), c.resolvedBranches(), c.examples().stream()
                .map(x -> new ExampleResponse(x.branch(), x.workItemNumber(), x.title())).toList())).toList());
        return new WorkItemPatternsResponse(p.enabled(), p.mode().name(),
                p.patterns().stream().map(e -> new PatternResponse(e.regex(), e.preset(), e.alsoInText())).toList(),
                WorkItemPatterns.defaultEntries().stream()
                        .map(e -> new PatternResponse(e.regex(), e.preset(), e.alsoInText())).toList(),
                List.of(new PresetResponse("AB_HASH", BranchPatternMatcher.AB_HASH_REGEX),
                        new PresetResponse("JIRA_KEY", BranchPatternMatcher.JIRA_KEY_REGEX),
                        new PresetResponse("NUMBER", BranchPatternMatcher.NUMBER_REGEX)),
                p.confirmedBy(), p.confirmedAt(), p.updatedBy(), p.updatedAt(), detection);
    }
}
