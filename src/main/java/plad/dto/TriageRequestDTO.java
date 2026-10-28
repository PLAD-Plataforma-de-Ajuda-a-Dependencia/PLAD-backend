package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TriageRequestDTO(
        @ArraySchema(arraySchema = @Schema(description = "One entry per question; every required question must be answered exactly once"))
        @NotEmpty @Size(max = 50) List<@NotNull @Valid AnswerRequestDTO> answers
) {
}
