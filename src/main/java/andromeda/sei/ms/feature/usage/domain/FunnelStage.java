package andromeda.sei.ms.feature.usage.domain;

/** Funnel stage. {@link #COMPLETE} is written only by the server, when the flow ends. */
public enum FunnelStage {
    OPEN, ENGAGE, STEP_ENTER, STEP_TIME, VERIFY_RESEND, VERIFY_FAIL, SUBMIT, SUBMIT_ERROR, CANCEL, COMPLETE;

    public boolean isVerify() {
        return this == VERIFY_RESEND || this == VERIFY_FAIL;
    }
}
