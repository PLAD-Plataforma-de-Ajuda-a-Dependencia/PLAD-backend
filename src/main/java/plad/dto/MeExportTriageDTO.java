package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "The dependent's triage with the text of each question")
public record MeExportTriageDTO(
        @Schema(description = "Triage id", example = "1")
        Long id,
        @Schema(description = "When the triage was first submitted (UTC)", example = "2026-10-27T21:15:30Z")
        Instant createdAt,
        @Schema(description = "When the triage was last resubmitted (UTC); equals created_at if never", example = "2026-10-27T21:15:30Z")
        Instant updatedAt,
        @ArraySchema(arraySchema = @Schema(description = "Stored answers, one entry per question, with the question text"))
        List<AnswerResponseDTO> answers
) {
}
