package andromeda.sei.ms.feature.vcs.application.port;

import andromeda.sei.ms.feature.vcs.domain.ReviewKind;

import java.time.Instant;

/**
 * One review signal already normalized by the provider's event mapper (neutral input DTO).
 *
 * @param kind       normalized kind; {@code null} only when {@code dismissed} (the provider no
 *                   longer reports the original state)
 * @param dismissed  the signal was later dismissed: the sync keeps the kind stored when it was
 *                   first seen, falling back to {@link ReviewKind#COMMENTED}
 * @param deleted    the provider reports the signal as deleted (an Azure Repos comment): a stored
 *                   event is marked deleted, and none is created when it was never stored
 * @param actorEmail provider identity email, a fallback for author resolution; may be {@code null}
 */
public record VcsReviewSignal(String externalEventId, String actorExternalId, String actorLogin, boolean bot,
                              ReviewKind kind, boolean dismissed, Instant occurredAt, boolean deleted,
                              String actorEmail) {

    public VcsReviewSignal(String externalEventId, String actorExternalId, String actorLogin, boolean bot,
                           ReviewKind kind, boolean dismissed, Instant occurredAt) {
        this(externalEventId, actorExternalId, actorLogin, bot, kind, dismissed, occurredAt, false, null);
    }
}
