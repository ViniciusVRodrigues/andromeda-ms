package andromeda.sei.ms.feature.usage.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.usage.application.export.CsvWriter;
import andromeda.sei.ms.feature.usage.domain.FunnelEvent;
import andromeda.sei.ms.feature.usage.domain.FunnelEventRepository;
import andromeda.sei.ms.feature.usage.domain.UsageEvent;
import andromeda.sei.ms.feature.usage.domain.UsageEventRepository;
import andromeda.sei.ms.feature.usage.domain.exception.UsageExportRangeInvalidException;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * CSV export of the organization's usage events plus its already-linked funnel events, merged by
 * {@code occurredAt} from two database cursors (never the whole period in memory). The organization comes
 * from the caller (the tenant), never from a request parameter. Anonymous funnels have no organization and
 * are never read here.
 */
@Service
@RequiredArgsConstructor
public class ExportUsageEventsUseCase {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    static final long MAX_DAYS = 366;

    public static final List<String> COLUMNS = List.of("occurred_at", "received_at", "user_id", "email", "type",
            "screen", "visible_ms", "element", "label", "area", "action", "value", "viewport", "session_id",
            "view_id", "origin", "status", "code", "flow", "stage", "step", "funnel_id");

    private final UsageEventRepository usageEventRepository;
    private final FunnelEventRepository funnelEventRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    /** Validated request: the half-open UTC period and the slug for the file name. */
    public record Export(String organizationId, String orgSlug, LocalDate from, LocalDate to,
                         Instant start, Instant end) {
    }

    /** Validates the period (before any byte is streamed, so errors leave as normal JSON). */
    public Export prepare(String organizationId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new UsageExportRangeInvalidException("'to' não pode ser anterior a 'from'.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new UsageExportRangeInvalidException("O período não pode passar de " + MAX_DAYS + " dias.");
        }
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        String slug = organizationRepository.findById(organizationId).map(Organization::getSlug).orElse("org");
        return new Export(organizationId, slug, from, to, start, end);
    }

    public void write(Export export, OutputStream target) throws IOException {
        Map<String, String> emails = new HashMap<>();
        for (User u : userRepository.findAllByOrganizationIdIncludingInactive(export.organizationId())) {
            emails.put(u.getId(), u.getEmail());
        }
        OutputStreamWriter writer = new OutputStreamWriter(target, StandardCharsets.UTF_8);
        CsvWriter csv = new CsvWriter(writer);
        csv.writeBom();
        csv.writeRow(COLUMNS);
        try (Stream<UsageEvent> usage = usageEventRepository.streamByOrganization(
                export.organizationId(), export.start(), export.end());
             Stream<FunnelEvent> funnel = funnelEventRepository.streamByOrganization(
                     export.organizationId(), export.start(), export.end())) {
            Iterator<UsageEvent> u = usage.iterator();
            Iterator<FunnelEvent> f = funnel.iterator();
            UsageEvent nextU = u.hasNext() ? u.next() : null;
            FunnelEvent nextF = f.hasNext() ? f.next() : null;
            while (nextU != null || nextF != null) {
                boolean takeUsage = nextF == null
                        || (nextU != null && !nextU.getOccurredAt().isAfter(nextF.getOccurredAt()));
                if (takeUsage) {
                    csv.writeRow(usageRow(nextU, emails));
                    nextU = u.hasNext() ? u.next() : null;
                } else {
                    /* Defensive: the query already filters by organization, anonymous rows never leave. */
                    if (nextF.getOrganizationId() != null) {
                        csv.writeRow(funnelRow(nextF, emails));
                    }
                    nextF = f.hasNext() ? f.next() : null;
                }
            }
        } finally {
            csv.flush();
        }
    }

    private static List<String> usageRow(UsageEvent e, Map<String, String> emails) {
        List<String> r = new ArrayList<>(COLUMNS.size());
        r.add(str(e.getOccurredAt()));
        r.add(str(e.getCreatedAt()));
        r.add(e.getUserId());
        r.add(e.getUserId() == null ? null : emails.get(e.getUserId()));
        r.add(e.getType().name());
        r.add(e.getScreen());
        r.add(str(e.getVisibleMs()));
        r.add(e.getElement());
        r.add(e.getLabel());
        r.add(e.getArea());
        r.add(e.getAction());
        r.add(e.getValue());
        r.add(e.getViewport());
        r.add(e.getSessionId());
        r.add(e.getViewId());
        r.add(e.getOrigin());
        r.add(str(e.getStatus()));
        r.add(e.getCode());
        r.add(null);
        r.add(null);
        r.add(null);
        r.add(null);
        return r;
    }

    private static List<String> funnelRow(FunnelEvent e, Map<String, String> emails) {
        List<String> r = new ArrayList<>(COLUMNS.size());
        r.add(str(e.getOccurredAt()));
        r.add(str(e.getCreatedAt()));
        r.add(e.getUserId());
        r.add(e.getUserId() == null ? null : emails.get(e.getUserId()));
        r.add("FUNNEL");
        r.add(null);
        r.add(str(e.getVisibleMs()));
        r.add(null);
        r.add(null);
        r.add(null);
        r.add(null);
        r.add(e.getValue());
        r.add(e.getViewport());
        r.add(null);
        r.add(null);
        r.add(null);
        r.add(null);
        r.add(null);
        r.add(e.getFlow() == null ? null : e.getFlow().value());
        r.add(e.getStage().name());
        r.add(e.getStep());
        r.add(e.getFunnelId());
        return r;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
