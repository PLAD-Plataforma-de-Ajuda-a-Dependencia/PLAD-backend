package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record TriageResponseDTO(
        @Schema(description = "Triage id", example = "1")
        Long id,
        @Schema(description = "When the triage was first submitted (UTC)", example = "2026-10-27T21:15:30Z")
        Instant createdAt,
        @ArraySchema(arraySchema = @Schema(description = "Stored answers, one entry per question"))
        List<AnswerResponseDTO> answers
) {
}
