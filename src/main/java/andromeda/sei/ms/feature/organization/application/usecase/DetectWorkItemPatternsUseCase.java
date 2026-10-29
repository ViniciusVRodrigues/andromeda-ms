package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.application.port.WorkItemPatternSampleSource;
import andromeda.sei.ms.feature.organization.application.port.WorkItemReferenceLookup;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic detection of the branch to work item patterns (agente-endpoint-v2, design 14.5).
 * Samples the distinct recent branches, scores a few candidate patterns against the organization's
 * active work items, picks them greedily by coverage and, when the mode allows and the numbers are
 * good enough, applies them with {@code mode: DETECTED}. Always records {@code detection}.
 */
@Component
public class DetectWorkItemPatternsUseCase {

    /** Why the detection runs (informational). */
    public enum Trigger { MANUAL, SCHEDULED, FIRST_AGENT_DEVICE }

    static final Duration LOOKBACK = Duration.ofDays(90);
    static final int MAX_BRANCHES = 5000;
    static final int MAX_CANDIDATES = 10;
    static final int MAX_PREFIXES = 5;
    static final int MIN_PREFIX_BRANCHES = 5;
    static final int MAX_EXAMPLES = 3;
    static final int LOOKUP_BATCH = 1000;
    static final int MIN_SAMPLE = 20;
    static final double MIN_PRECISION = 0.8;
    static final double MIN_GAIN = 0.05;
    static final double MIN_APPLY_COVERAGE = 0.5;
    private static final double EPS = 1e-9;
    private static final int WRITE_ATTEMPTS = 3;
    private static final Set<String> IGNORED_BRANCHES = Set.of("main", "master", "develop", "HEAD");
    /** A literal prefix before a number: letters then {@code #}, {@code -} or {@code _}, after {@code /} or the start. */
    private static final Pattern PREFIX_SCAN = Pattern.compile("(?i)(?:^|/)([a-z]+[#_-])\\d");

    private final OrganizationRepository organizationRepository;
    private final WorkItemPatternSampleSource sampleSource;
    private final WorkItemReferenceLookup lookup;
    private final Clock clock;

    @Autowired
    public DetectWorkItemPatternsUseCase(OrganizationRepository organizationRepository,
                                         WorkItemPatternSampleSource sampleSource, WorkItemReferenceLookup lookup) {
        this(organizationRepository, sampleSource, lookup, Clock.systemUTC());
    }

    DetectWorkItemPatternsUseCase(OrganizationRepository organizationRepository,
                                  WorkItemPatternSampleSource sampleSource, WorkItemReferenceLookup lookup,
                                  Clock clock) {
        this.organizationRepository = organizationRepository;
        this.sampleSource = sampleSource;
        this.lookup = lookup;
        this.clock = clock;
    }

    /** Runs the detection for the organization and returns the stored patterns (with the new {@code detection}). */
    public WorkItemPatterns execute(String organizationId, Trigger trigger) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
        Instant now = clock.instant();

        List<String> branches = sample(organizationId, now);
        List<Scored> scored = score(organizationId, branches);
        Selection selection = select(scored, branches.size());
        return store(organizationId, now, branches.size(), scored, selection);
    }

    private List<String> sample(String organizationId, Instant now) {
        List<String> raw = sampleSource.recentBranches(organizationId, now.minus(LOOKBACK), MAX_BRANCHES);
        Set<String> distinct = new LinkedHashSet<>();
        for (String branch : raw) {
            if (branch == null || branch.isBlank() || IGNORED_BRANCHES.contains(branch.trim())) continue;
            distinct.add(branch);
            if (distinct.size() == MAX_BRANCHES) break;
        }
        return List.copyOf(distinct);
    }

    // ---- candidates and metrics -------------------------------------------------------------------

    /** A candidate regex with what it captured on each branch (index in the sample). */
    private record Raw(String regex, String preset, Map<Integer, String> captures) {}

    private record Scored(Raw raw, BitSet resolved, WorkItemPatterns.Candidate candidate) {}

    private List<Scored> score(String organizationId, List<String> branches) {
        List<String> regexes = new ArrayList<>();
        regexes.add(BranchPatternMatcher.AB_HASH_REGEX);
        if (lookup.hasJiraStyleExternalIds(organizationId)) regexes.add(BranchPatternMatcher.JIRA_KEY_REGEX);
        regexes.add(BranchPatternMatcher.NUMBER_REGEX);
        prefixes(branches).forEach(p -> regexes.add(BranchPatternMatcher.prefixRegex(p)));
        List<String> limited = regexes.size() > MAX_CANDIDATES ? regexes.subList(0, MAX_CANDIDATES) : regexes;

        List<Raw> raws = new ArrayList<>();
        Set<String> numbers = new LinkedHashSet<>();
        for (String regex : limited) {
            BranchPatternMatcher matcher = BranchPatternMatcher.of(List.of(regex));
            Map<Integer, String> captures = new TreeMap<>();
            for (int i = 0; i < branches.size(); i++) {
                Optional<String> hit = matcher.match(branches.get(i));
                if (hit.isPresent()) {
                    captures.put(i, hit.get());
                    numbers.add(hit.get());
                }
            }
            raws.add(new Raw(regex, BranchPatternMatcher.classify(regex), captures));
        }

        Map<String, String> titles = titles(organizationId, numbers);
        List<Scored> out = new ArrayList<>();
        for (Raw raw : raws) {
            BitSet resolved = new BitSet(branches.size());
            List<WorkItemPatterns.Example> examples = new ArrayList<>();
            for (Map.Entry<Integer, String> capture : raw.captures().entrySet()) {
                String title = titles.get(capture.getValue());
                if (title == null) continue;
                resolved.set(capture.getKey());
                if (examples.size() < MAX_EXAMPLES) {
                    examples.add(new WorkItemPatterns.Example(branches.get(capture.getKey()), capture.getValue(), title));
                }
            }
            int matched = raw.captures().size();
            int resolvedCount = resolved.cardinality();
            double coverage = branches.isEmpty() ? 0 : round((double) resolvedCount / branches.size());
            double precision = matched == 0 ? 0 : round((double) resolvedCount / matched);
            out.add(new Scored(raw, resolved, new WorkItemPatterns.Candidate(raw.regex(), raw.preset(), coverage,
                    precision, matched, resolvedCount, examples)));
        }
        return out;
    }

    /** Literal prefixes seen before a number in at least 5 branches: the 5 most frequent, ties by name. */
    private static List<String> prefixes(List<String> branches) {
        Map<String, Integer> counts = new HashMap<>();
        for (String branch : branches) {
            String cut = branch.length() > BranchPatternMatcher.MAX_BRANCH_LENGTH
                    ? branch.substring(0, BranchPatternMatcher.MAX_BRANCH_LENGTH) : branch;
            Set<String> seen = new LinkedHashSet<>();
            Matcher m = PREFIX_SCAN.matcher(cut);
            while (m.find()) seen.add(m.group(1).toLowerCase(Locale.ROOT));
            seen.forEach(p -> counts.merge(p, 1, Integer::sum));
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_PREFIX_BRANCHES)
                .filter(e -> !"ab#".equals(e.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_PREFIXES)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<String, String> titles(String organizationId, Set<String> numbers) {
        Map<String, String> titles = new HashMap<>();
        List<String> all = new ArrayList<>(numbers);
        for (int from = 0; from < all.size(); from += LOOKUP_BATCH) {
            List<String> batch = all.subList(from, Math.min(all.size(), from + LOOKUP_BATCH));
            titles.putAll(lookup.activeTitlesByExternalId(organizationId, batch));
        }
        return titles;
    }

    // ---- selection --------------------------------------------------------------------------------

    private record Selection(List<Scored> selected, double coverage) {}

    /** AB_HASH always stays; the others enter by coverage when precise enough and adding at least 5 points. */
    private static Selection select(List<Scored> scored, int total) {
        List<Scored> selected = new ArrayList<>();
        BitSet union = new BitSet(total);
        scored.stream().filter(s -> "AB_HASH".equals(s.raw().preset())).findFirst().ifPresent(s -> {
            selected.add(s);
            union.or(s.resolved());
        });
        List<Scored> others = scored.stream().filter(s -> !"AB_HASH".equals(s.raw().preset()))
                .sorted(Comparator.comparingDouble((Scored s) -> s.candidate().coverage()).reversed())
                .toList();
        for (Scored s : others) {
            if (total == 0 || s.candidate().precision() < MIN_PRECISION) continue;
            BitSet added = (BitSet) s.resolved().clone();
            added.andNot(union);
            if ((double) added.cardinality() / total >= MIN_GAIN - EPS) {
                selected.add(s);
                union.or(s.resolved());
            }
        }
        double coverage = total == 0 ? 0 : round((double) union.cardinality() / total);
        return new Selection(selected, coverage);
    }

    // ---- write ------------------------------------------------------------------------------------

    private WorkItemPatterns store(String organizationId, Instant now, int scanned, List<Scored> scored,
                                   Selection selection) {
        boolean good = scanned >= MIN_SAMPLE && selection.coverage() >= MIN_APPLY_COVERAGE - EPS;
        OptimisticLockingFailureException last = null;
        for (int attempt = 0; attempt < WRITE_ATTEMPTS; attempt++) {
            Organization organization = organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
            WorkItemPatterns current = organization.getWorkItemPatterns();
            boolean apply = good && (current.mode() == WorkItemPatterns.Mode.DEFAULT
                    || current.mode() == WorkItemPatterns.Mode.DETECTED);
            WorkItemPatterns.Detection detection = new WorkItemPatterns.Detection(now, scanned,
                    selection.coverage(), apply, scored.stream().map(Scored::candidate).toList());
            WorkItemPatterns updated;
            if (apply) {
                List<WorkItemPatterns.Entry> entries = selection.selected().stream()
                        .map(s -> WorkItemPatterns.Entry.of(s.raw().regex(),
                                WorkItemPatterns.Entry.defaultAlsoInText(s.raw().regex())))
                        .toList();
                updated = new WorkItemPatterns(current.enabled(), WorkItemPatterns.Mode.DETECTED, entries,
                        current.confirmedBy(), current.confirmedAt(), null, now, detection);
            } else {
                updated = new WorkItemPatterns(current.enabled(), current.mode(), current.patterns(),
                        current.confirmedBy(), current.confirmedAt(), current.updatedBy(), current.updatedAt(),
                        detection);
            }
            organization.setWorkItemPatterns(updated);
            try {
                return organizationRepository.save(organization).getWorkItemPatterns();
            } catch (OptimisticLockingFailureException e) {
                last = e;
            }
        }
        throw last;
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
