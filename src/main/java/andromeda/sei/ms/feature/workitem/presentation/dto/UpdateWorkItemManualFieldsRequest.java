package andromeda.sei.ms.feature.workitem.presentation.dto;

/**
 * complexity/effort are only applied when their corresponding JSON key was present in the
 * request body — presence is detected via Jackson's @JsonProperty-driven binding combined
 * with explicit clear flags, so "omitted" (leave unchanged) is distinguishable from
 * "explicitly cleared back to the Azure-sync-managed value".
 *
 * <p>The clear flags are boxed {@link Boolean}, not primitive {@code boolean}: a partial update
 * like {@code {"effort": 9}} legitimately omits them, and Jackson refuses to bind a missing/null
 * JSON value into a primitive ("Cannot map `null` into type `boolean`"), which failed the whole
 * request before it ever reached the controller. Use {@link #clearComplexityOverrideOrFalse()} /
 * {@link #clearEffortOverrideOrFalse()} to read them with the intended "absent means false"
 * semantics instead of unboxing directly.
 */
public record UpdateWorkItemManualFieldsRequest(
        Double complexity,
        Boolean clearComplexityOverride,
        Double effort,
        Boolean clearEffortOverride
) {
    public boolean clearComplexityOverrideOrFalse() {
        return Boolean.TRUE.equals(clearComplexityOverride);
    }

    public boolean clearEffortOverrideOrFalse() {
        return Boolean.TRUE.equals(clearEffortOverride);
    }
}
