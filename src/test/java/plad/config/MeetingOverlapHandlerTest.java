package plad.config;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import plad.dto.ErrorResponseDTO;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

// Two requests can pass MeetingService's overlap check together; the exclusion constraint (V25) then rejects one at the
// flush. The handler must turn that into the same 409 MEETING_CONFLICT, not into the generic DATA_CONFLICT.
class MeetingOverlapHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void theOverlapConstraintBecomesMeetingConflict() {
        DataIntegrityViolationException ex = violation("meetings_no_overlap");

        ResponseEntity<ErrorResponseDTO> response = handler.handleDataIntegrity(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("MEETING_CONFLICT");
    }

    @Test
    void anyOtherConstraintStaysDataConflict() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleDataIntegrity(violation("uq_something_else"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("DATA_CONFLICT");
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", new SQLException("x", "23P01"), constraint));
    }
}
