package plad.config;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import plad.dto.ErrorResponseDTO;
import plad.dto.FieldErrorDTO;
import plad.exception.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// ResponseEntityExceptionHandler maps Spring's own exceptions (bad JSON, 404, 405...) to the right status;
// handleExceptionInternal below gives them the same body as ours
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCredentials(InvalidCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex); // 401
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmailInUse(EmailAlreadyInUseException ex) {
        return error(HttpStatus.CONFLICT, ex); // 409
    }

    @ExceptionHandler(TooManyRequestException.class)
    public ResponseEntity<ErrorResponseDTO> handleTooManyRequests(TooManyRequestException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS) // 429
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ErrorResponseDTO(ex.getCode().name(), ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(InvalidRegistrationException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidRegistration(InvalidRegistrationException ex) {
        return error(HttpStatus.BAD_REQUEST, ex); // 400
    }

    @ExceptionHandler(InvalidTriageException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidTriage(InvalidTriageException ex) {
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(TriageAlreadyExistException.class)
    public ResponseEntity<ErrorResponseDTO> handleTriageExists(TriageAlreadyExistException ex) {
        return error(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(TriageNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleTriageNotFound(TriageNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ForbiddenActionException.class)
    public ResponseEntity<ErrorResponseDTO> handleForbidden(ForbiddenActionException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    // safety net for unique constraints a service did not translate; never echo constraint names
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrity(DataIntegrityViolationException ex) {
        // the Postgres message carries the offending value (e.g. a duplicated e-mail): log the constraint name only
        log.warn("Unhandled data integrity violation (constraint: {})", violatedConstraint(ex));
        return error(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT, "Request conflicts with existing data"); // 409
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Internal server error"); // 500
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldErrorDTO> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDTO(toSnakeCase(error.getField()), error.getDefaultMessage()))
                .toList();
        String message = errors.stream()
                .map(error -> error.field() + ": " + error.message())
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "Validation failed";
        }
        return ResponseEntity.status(status).headers(headers) // 400
                .body(new ErrorResponseDTO(ErrorCode.VALIDATION_ERROR.name(), message, Instant.now(), errors));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        // ProblemDetail.detail is Spring's generic text ("Failed to read request"), not the parser's internals
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : reasonPhrase(statusCode);
        return super.handleExceptionInternal(ex, new ErrorResponseDTO(statusName(statusCode), message, Instant.now()),
                headers, statusCode, request);
    }

    private static ResponseEntity<ErrorResponseDTO> error(HttpStatus status, ApiException ex) {
        return error(status, ex.getCode(), ex.getMessage());
    }

    private static ResponseEntity<ErrorResponseDTO> error(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponseDTO(code.name(), message, Instant.now()));
    }

    private static String violatedConstraint(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
        }
        return "unknown";
    }

    // errors Spring maps itself have no ErrorCode: the status name (NOT_FOUND, METHOD_NOT_ALLOWED...) is the code
    private static String statusName(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.name() : "REQUEST_FAILED";
    }

    private static String reasonPhrase(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.getReasonPhrase() : "Request failed";
    }

    // field paths come from the Java records; clients send snake_case (spring.jackson.property-naming-strategy)
    // "answers[0].questionId" -> "answers[0].question_id"
    private static String toSnakeCase(String fieldPath) {
        return CAMEL_CASE_BOUNDARY.matcher(fieldPath).replaceAll("$1_$2").toLowerCase(Locale.ROOT);
    }
}
