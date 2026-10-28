package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AnswerResponseDTO(
        @Schema(description = "Question id", example = "1")
        Long questionId,
        @Schema(description = "Question text", example = "What is your age range?")
        String questionText,
        @ArraySchema(arraySchema = @Schema(description = "Stored option codes, or the free text", example = "[\"AGE_25_39\"]"))
        List<String> values
) {
}
