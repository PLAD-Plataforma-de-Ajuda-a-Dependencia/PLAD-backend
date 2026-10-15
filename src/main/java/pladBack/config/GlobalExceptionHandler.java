package pladBack.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pladBack.DTO.ErrorResponseDTO;
import pladBack.exception.*;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCredentials(InvalidCredentialsException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(ex.getMessage(), Instant.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error); // 401
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmailInUse(EmailAlreadyInUseException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(ex.getMessage(), Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error); // 409
    }

    @ExceptionHandler(TooManyRequestException.class)
    public ResponseEntity<ErrorResponseDTO> handleTooManyRequests(TooManyRequestException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(ex.getMessage(), Instant.now());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error); // 429
    }

    @ExceptionHandler(InvalidRegistrationException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidRegistration(InvalidRegistrationException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(ex.getMessage(), Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error); // 400
    }

    @ExceptionHandler(InvalidTriageException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidTriage(InvalidTriageException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDTO(ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(TriageAlreadyExistException.class)
    public ResponseEntity<ErrorResponseDTO> handleTriageExists(TriageAlreadyExistException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDTO(ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(TriageNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleTriageNotFound(TriageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDTO(ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(ForbiddenActionException.class)
    public ResponseEntity<ErrorResponseDTO> handleForbidden(ForbiddenActionException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponseDTO(ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "Validation failed";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDTO(message, Instant.now())); // 400
    }

}
