package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ErrorResponseDTO(
    @Schema(description = "What went wrong; validation errors list field: reason pairs separated by \"; \"",
            example = "email: must be a well-formed email address")
    String message,
    @Schema(description = "When the error was produced (UTC)", example = "2026-10-27T21:15:30Z")
    Instant timestamp
) {}
