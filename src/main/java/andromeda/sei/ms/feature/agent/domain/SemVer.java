package andromeda.sei.ms.feature.agent.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal semantic version: MAJOR.MINOR.PATCH with an optional pre-release (lower than the release). */
public record SemVer(int major, int minor, int patch, String preRelease) implements Comparable<SemVer> {

    private static final Pattern FORMAT =
            Pattern.compile("^v?(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})(?:-([0-9A-Za-z.-]{1,40}))?(?:\\+[0-9A-Za-z.-]{1,40})?$");

    public static Optional<SemVer> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = FORMAT.matcher(text.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new SemVer(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), m.group(4)));
    }

    /** True when {@code version} parses and is not below {@code minimum}. Unparseable counts as below. */
    public static boolean isAtLeast(String version, String minimum) {
        Optional<SemVer> v = parse(version);
        Optional<SemVer> min = parse(minimum);
        if (min.isEmpty()) {
            return true;
        }
        return v.isPresent() && v.get().compareTo(min.get()) >= 0;
    }

    @Override
    public int compareTo(SemVer o) {
        int c = Integer.compare(major, o.major);
        if (c != 0) return c;
        c = Integer.compare(minor, o.minor);
        if (c != 0) return c;
        c = Integer.compare(patch, o.patch);
        if (c != 0) return c;
        if (preRelease == null && o.preRelease == null) return 0;
        if (preRelease == null) return 1;
        if (o.preRelease == null) return -1;
        return comparePreRelease(preRelease, o.preRelease);
    }

    private static int comparePreRelease(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            boolean na = pa[i].chars().allMatch(Character::isDigit);
            boolean nb = pb[i].chars().allMatch(Character::isDigit);
            int c;
            if (na && nb) {
                c = Long.compare(Long.parseLong(pa[i]), Long.parseLong(pb[i]));
            } else if (na) {
                c = -1;
            } else if (nb) {
                c = 1;
            } else {
                c = pa[i].compareTo(pb[i]);
            }
            if (c != 0) return c;
        }
        return Integer.compare(pa.length, pb.length);
    }
}
