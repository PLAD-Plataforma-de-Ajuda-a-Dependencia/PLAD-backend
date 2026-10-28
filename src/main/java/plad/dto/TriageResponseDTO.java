package plad.dto;

import java.time.Instant;
import java.util.List;

public record TriageResponseDTO(
        Long id,
        Instant createdAt,
        List<AnswerResponseDTO> answers
) {
}
