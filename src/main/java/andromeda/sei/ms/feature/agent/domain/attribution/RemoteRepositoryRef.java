package andromeda.sei.ms.feature.agent.domain.attribution;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A git remote reduced to what identifies the repository in the version-control slice, always
 * lowercase: {@code GITHUB} carries {@code owner/repo} as {@code name}; {@code AZURE_REPOS}
 * carries the repository name only (the Azure organization and project are kept for reference).
 *
 * <p>{@link #parse} accepts https and ssh remotes, with or without credentials and {@code .git}:
 * {@code github.com/o/r}, {@code git@github.com:o/r.git}, {@code dev.azure.com/org/proj/repo},
 * {@code dev.azure.com/org/proj/_git/repo}, {@code org.visualstudio.com/proj/_git/repo},
 * {@code ssh.dev.azure.com:v3/org/proj/repo}.
 */
public record RemoteRepositoryRef(Kind kind, String name, String azureOrganization, String azureProject) {

    public enum Kind { GITHUB, AZURE_REPOS }

    public static Optional<RemoteRepositoryRef> parse(String raw) {
        if (raw == null) return Optional.empty();
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.length() > 500) return Optional.empty();
        int scheme = s.indexOf("://");
        if (scheme > 0) s = s.substring(scheme + 3);
        int cut = indexOfAny(s, '?', '#');
        if (cut >= 0) s = s.substring(0, cut);
        int slash = s.indexOf('/');
        String authority = slash < 0 ? s : s.substring(0, slash);
        int at = authority.lastIndexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        // scp-like "host:path" -> "host/path"; a port ("host:443/path") is dropped
        slash = s.indexOf('/');
        int colon = s.indexOf(':');
        if (colon >= 0 && (slash < 0 || colon < slash)) {
            String afterColon = s.substring(colon + 1);
            String head = s.substring(0, colon);
            s = afterColon.matches("^[0-9]+/.*") ? head + afterColon.substring(afterColon.indexOf('/'))
                    : head + "/" + afterColon;
        }
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        List<String> parts = new ArrayList<>();
        for (String p : s.split("/")) {
            if (!p.isEmpty()) parts.add(decode(p));
        }
        if (parts.size() < 2) return Optional.empty();
        String host = parts.get(0);
        List<String> path = parts.subList(1, parts.size());

        if (host.equals("github.com") || host.equals("www.github.com")) {
            if (path.size() != 2) return Optional.empty();
            return Optional.of(new RemoteRepositoryRef(Kind.GITHUB, path.get(0) + "/" + path.get(1), null, null));
        }
        if (host.equals("dev.azure.com")) {
            return azure(path.get(0), path.subList(1, path.size()));
        }
        if (host.equals("ssh.dev.azure.com")) {
            if (path.size() != 4 || !path.get(0).equals("v3")) return Optional.empty();
            return azure(path.get(1), path.subList(2, path.size()));
        }
        if (host.endsWith(".visualstudio.com")) {
            String org = host.substring(0, host.length() - ".visualstudio.com".length());
            List<String> rest = path.get(0).equals("defaultcollection") ? path.subList(1, path.size()) : path;
            return org.isEmpty() ? Optional.empty() : azure(org, rest);
        }
        return Optional.empty();
    }

    /** {@code rest} is {@code project/repo} or {@code project/_git/repo}. */
    private static Optional<RemoteRepositoryRef> azure(String org, List<String> rest) {
        List<String> segs = rest.stream().filter(p -> !p.equals("_git")).toList();
        if (segs.size() != 2) return Optional.empty();
        return Optional.of(new RemoteRepositoryRef(Kind.AZURE_REPOS, segs.get(1), org, segs.get(0)));
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);
    }

    private static String decode(String segment) {
        try {
            return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return segment;
        }
    }
}
