package andromeda.sei.ms.feature.usage.domain;

/** LOGIN is written by the server only; the client may send the other five. */
public enum UsageEventType {
    PAGE_VIEW, SCREEN_TIME, CLICK, ACTION, ERROR, LOGIN
}
