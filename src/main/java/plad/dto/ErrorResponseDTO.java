package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record ErrorResponseDTO(
    @Schema(description = "Stable identifier of the error, safe to switch on (see the error code list in CLAUDE.md)",
            example = "VALIDATION_ERROR")
    String code,
    @Schema(description = "What went wrong; validation errors list field: reason pairs separated by \"; \"",
            example = "email: must be a well-formed email address")
    String message,
    @Schema(description = "When the error was produced (UTC)", example = "2026-10-27T21:15:30Z")
    Instant timestamp,
    @ArraySchema(arraySchema = @Schema(description = "Per-field details; empty unless the code is VALIDATION_ERROR"))
    List<FieldErrorDTO> errors
) {

    public ErrorResponseDTO(String code, String message, Instant timestamp) {
        this(code, message, timestamp, List.of());
    }
}
