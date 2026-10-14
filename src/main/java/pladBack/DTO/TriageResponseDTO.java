package pladBack.DTO;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public record TriageResponseDTO(
        Long id,
        Instant createdAt,
        List<@NotBlank AnswerResponseDTO> answer
) {
}
