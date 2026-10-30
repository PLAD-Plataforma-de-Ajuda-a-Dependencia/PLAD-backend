package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TriageRequestDTO(
        @ArraySchema(arraySchema = @Schema(description = "One entry per answered question; every question is optional and can be answered at most once"))
        @NotNull @Size(max = 50) List<@NotNull @Valid AnswerRequestDTO> answers
) {
}
