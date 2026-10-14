package pladBack.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TriageRequestDTO(
        @NotNull List<@NotBlank AnswerRequestDTO> answer
) {
}
