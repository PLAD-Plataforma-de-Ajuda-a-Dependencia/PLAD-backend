package plad.exception;

// stable identifiers clients can switch on; the name is what goes in ErrorResponseDTO.code
public enum ErrorCode {
    EMAIL_IN_USE,
    INVALID_CREDENTIALS,
    INVALID_PASSWORD_CHANGE,
    INVALID_REFRESH_TOKEN,
    INVALID_REGISTRATION,
    INVALID_TRIAGE,
    CONSENT_REQUIRED,
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
