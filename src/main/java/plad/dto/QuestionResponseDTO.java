package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import plad.entity.QuestionType;

import java.util.List;

public record QuestionResponseDTO(
        @Schema(description = "Question id", example = "1")
        Long id,
        @Schema(description = "Stable question code, the same in every environment", example = "AGE_RANGE")
        String code,
        @Schema(description = "Question text", example = "What is your age range?")
        String questionText,
        @Schema(description = "single_choice takes one option, multi_choice several, free_text a text")
        QuestionType questionType,
        @ArraySchema(arraySchema = @Schema(description = "Selectable options; empty for free_text"))
        List<OptionDTO> options,
        @Schema(description = "Position in the questionnaire, ascending", example = "1")
        short displayOrder,
        @Schema(description = "Whether the question must be answered", example = "true")
        boolean required
) {
}
