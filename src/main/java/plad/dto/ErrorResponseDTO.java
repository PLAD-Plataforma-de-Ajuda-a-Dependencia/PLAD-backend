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
    List<FieldErrorDTO> errors,
    @Schema(description = "Request id to give to support; null unless the code is INTERNAL_ERROR",
            example = "3f0c9a52-6b1e-4d7a-9a52-0d8c1f6e2b47", nullable = true)
    String trace
) {

    public ErrorResponseDTO(String code, String message, Instant timestamp) {
        this(code, message, timestamp, List.of(), null);
    }

    public ErrorResponseDTO(String code, String message, Instant timestamp, List<FieldErrorDTO> errors) {
        this(code, message, timestamp, errors, null);
    }
}
