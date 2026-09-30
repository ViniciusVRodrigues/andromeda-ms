package andromeda.sei.ms.feature.vcs.presentation.dto;

import java.util.List;

/** Identity ids, unique names or display names the organization flags as service accounts. */
public record ServiceIdentitiesRequest(List<String> identities) {}
