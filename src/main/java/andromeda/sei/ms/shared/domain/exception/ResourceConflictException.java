package andromeda.sei.ms.shared.domain.exception;

public class ResourceConflictException extends DomainException {

    public ResourceConflictException(String code, String message) {
        super(code, message);
    }
}
