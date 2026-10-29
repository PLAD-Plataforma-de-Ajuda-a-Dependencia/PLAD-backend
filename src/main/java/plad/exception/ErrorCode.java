package plad.exception;

// stable identifiers clients can switch on; the name is what goes in ErrorResponseDTO.code
public enum ErrorCode {
    EMAIL_IN_USE,
    INVALID_CREDENTIALS,
    INVALID_REGISTRATION,
    INVALID_TRIAGE,
    TRIAGE_ALREADY_EXISTS,
    TRIAGE_NOT_FOUND,
    FORBIDDEN,
    VALIDATION_ERROR,
    RATE_LIMITED,
    UNAUTHORIZED,
    DATA_CONFLICT,
    SERVICE_UNAVAILABLE,
    INTERNAL_ERROR
}
