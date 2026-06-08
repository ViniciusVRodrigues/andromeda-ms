package andromeda.sei.ms.feature.project.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

public class ProjectSlugConflictException extends ResourceConflictException {

    public ProjectSlugConflictException(String slug) {
        super("PROJECT_SLUG_CONFLICT", "Slug already in use: " + slug);
    }
}
