package andromeda.sei.ms.shared.domain.exception;

public class ForbiddenOperationException extends DomainException {

    public ForbiddenOperationException(String code, String message) {
        super(code, message);
    }
}
