package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AnswerRequestDTO(
        @Schema(description = "Id of a question from GET /triage/questions", example = "1")
        @NotNull Long questionId,
        // @ArraySchema: a plain @Schema on a List also lands on its items (and on the schema of a DTO item)
        @ArraySchema(arraySchema = @Schema(example = "[\"AGE_25_39\"]",
                description = "Option codes for single_choice and multi_choice questions (single_choice takes exactly one); "
                        + "the text itself, as the only item, for free_text"))
        @NotEmpty @Size(max = 20) List<@NotBlank String> values
) {
}
