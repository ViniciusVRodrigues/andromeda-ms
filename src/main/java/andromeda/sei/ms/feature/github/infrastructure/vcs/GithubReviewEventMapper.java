package andromeda.sei.ms.feature.github.infrastructure.vcs;

import andromeda.sei.ms.feature.github.infrastructure.client.GhReviewDto;
import andromeda.sei.ms.feature.vcs.application.port.VcsReviewSignal;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;

import java.util.Locale;
import java.util.Optional;

/**
 * GitHub review payload → normalized review signal (vcs-core spec "Normalized review events"):
 * {@code APPROVED}, {@code CHANGES_REQUESTED} and {@code COMMENTED} map 1:1; {@code DISMISSED} is
 * flagged so the sync keeps the kind it had when submitted (falling back to {@code COMMENTED});
 * {@code PENDING} (not submitted yet) and unknown states are not stored.
 */
public final class GithubReviewEventMapper {

    private static final String DISMISSED = "DISMISSED";

    private GithubReviewEventMapper() {}

    public static Optional<VcsReviewSignal> toSignal(GhReviewDto dto) {
        if (dto == null || dto.state() == null) return Optional.empty();
        String state = dto.state().toUpperCase(Locale.ROOT);
        boolean dismissed = DISMISSED.equals(state);
        ReviewKind kind = dismissed ? null : kindOf(state);
        if (!dismissed && kind == null) return Optional.empty();

        GhReviewDto.UserDto user = dto.user();
        return Optional.of(new VcsReviewSignal(
                String.valueOf(dto.id()),
                user != null && user.id() != null ? String.valueOf(user.id()) : null,
                user != null ? user.login() : null,
                user != null && user.isBot(),
                kind,
                dismissed,
                GithubInstants.parse(dto.submittedAt())));
    }

    private static ReviewKind kindOf(String state) {
        return switch (state) {
            case "APPROVED" -> ReviewKind.APPROVED;
            case "CHANGES_REQUESTED" -> ReviewKind.CHANGES_REQUESTED;
            case "COMMENTED" -> ReviewKind.COMMENTED;
            default -> null;
        };
    }
}
