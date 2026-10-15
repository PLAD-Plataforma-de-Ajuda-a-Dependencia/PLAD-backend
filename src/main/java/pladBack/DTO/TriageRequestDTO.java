package pladBack.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TriageRequestDTO(
        @NotNull List<@NotNull @Valid AnswerRequestDTO> answers
) {
}
