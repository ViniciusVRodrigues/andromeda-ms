package andromeda.sei.ms.feature.sync.application.runner;

import andromeda.sei.ms.shared.domain.exception.DomainException;

/** Traduz uma excecao de sync num codigo {@code ENTITY_REASON} e numa mensagem curta. */
final class SyncFailures {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private SyncFailures() {}

    static String codeOf(Exception e, String fallbackCode) {
        return e instanceof DomainException domain ? domain.getCode() : fallbackCode;
    }

    static String messageOf(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }
}
