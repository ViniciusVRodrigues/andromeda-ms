package andromeda.sei.ms.feature.usage.domain;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

/** Format rules of the usage-event contract. The patterns are constants so the DTO can reuse them. */
public final class UsageEventRules {

    public static final String SCREEN_REGEX = "^[a-z]+(-[a-z]+)*(/[a-z]+(-[a-z]+)*){0,3}$";
    public static final int SCREEN_MAX_LENGTH = 80;
    public static final String UUID_V4_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    public static final String VIEWPORT_REGEX = "^(sm|md|lg)$";
    public static final String ELEMENT_REGEX = "^(button|link|tab|menuitem|option|checkbox|radio|switch|summary|select)$";
    public static final String AREA_REGEX = "^[a-z][a-z-]{0,31}$";
    public static final String ACTION_REGEX = "^[A-Z][A-Z_]{2,40}$";
    public static final String VALUE_REGEX = "^[a-z0-9-]{1,16}$";
    public static final String ORIGIN_REGEX = "^(api|runtime|render)$";
    public static final String CODE_REGEX = "^[A-Z][A-Z0-9_]{1,63}$";
    public static final String CLIENT_TYPE_REGEX = "^(PAGE_VIEW|SCREEN_TIME|CLICK|ACTION|ERROR)$";

    public static final long MAX_VISIBLE_MS = 86_400_000L;
    public static final int MAX_STATUS = 599;
    public static final int UNAUTHORIZED = 401;

    public static final Duration CLOCK_PAST_TOLERANCE = Duration.ofHours(24);
    public static final Duration CLOCK_FUTURE_TOLERANCE = Duration.ofMinutes(5);

    static final Pattern SCREEN = Pattern.compile(SCREEN_REGEX);
    static final Pattern UUID_V4 = Pattern.compile(UUID_V4_REGEX);
    static final Pattern AREA = Pattern.compile(AREA_REGEX);
    static final Pattern ACTION = Pattern.compile(ACTION_REGEX);
    static final Pattern VALUE = Pattern.compile(VALUE_REGEX);
    static final Pattern CODE = Pattern.compile(CODE_REGEX);

    static final Set<String> VIEWPORTS = Set.of("sm", "md", "lg");
    static final Set<String> ELEMENTS = Set.of("button", "link", "tab", "menuitem", "option", "checkbox",
            "radio", "switch", "summary", "select");
    static final Set<String> ORIGINS = Set.of("api", "runtime", "render");

    private UsageEventRules() {
    }
}
