package andromeda.sei.ms.feature.dashboard.presentation.dto.v2;

/** A project member the individual dashboard can be opened for. No e-mail; {@code jobTitle} may be null. */
public record DashboardPersonResponse(String id, String name, String jobTitle) {}
