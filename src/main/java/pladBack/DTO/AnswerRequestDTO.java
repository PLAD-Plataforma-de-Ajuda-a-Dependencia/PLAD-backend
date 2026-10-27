package pladBack.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AnswerRequestDTO(
        @NotNull Long questionId,
        @NotEmpty @Size(max = 20) List<@NotBlank String> values
) {
}