package andromeda.sei.ms.shared.domain.exception;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }
}
