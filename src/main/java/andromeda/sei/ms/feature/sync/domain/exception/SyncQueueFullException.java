package andromeda.sei.ms.feature.sync.domain.exception;

import andromeda.sei.ms.shared.domain.exception.TooManyRequestsException;

/** A fila do executor de sync esta cheia (HTTP 429 + {@code Retry-After}). */
public class SyncQueueFullException extends TooManyRequestsException {

    public static final long RETRY_AFTER_SECONDS = 10;

    public SyncQueueFullException() {
        super("SYNC_QUEUE_FULL", "Too many syncs in progress; retry in " + RETRY_AFTER_SECONDS + "s",
                RETRY_AFTER_SECONDS);
    }
}
