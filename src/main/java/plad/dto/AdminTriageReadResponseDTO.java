package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "A triage as read by an admin; the dependent is identified by id and nickname, never by real name or e-mail")
public record AdminTriageReadResponseDTO(
        @Schema(description = "Triage id", example = "1")
        Long id,
        @Schema(description = "User id of the dependent who answered", example = "7")
        Long dependentId,
        @Schema(description = "The dependent's nickname")
        DependentNicknameDTO dependent,
        @Schema(description = "When the triage was first submitted (UTC)", example = "2026-10-27T21:15:30Z")
        Instant createdAt,
        @ArraySchema(arraySchema = @Schema(description = "Stored answers, one entry per question"))
        List<AnswerResponseDTO> answers
) {
}
