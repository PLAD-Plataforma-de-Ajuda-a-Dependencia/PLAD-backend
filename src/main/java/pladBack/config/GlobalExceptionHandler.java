package pladBack.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pladBack.DTO.ErrorResponseDTO;
import pladBack.exception.EmailAlreadyInUseException;
import pladBack.exception.InvalidCredentialsException;
import pladBack.exception.TooManyRequestException;

import java.time.Instant;

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

}
