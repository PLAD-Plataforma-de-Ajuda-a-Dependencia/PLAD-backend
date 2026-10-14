package pladBack.DTO;

import java.util.List;

public record AnswerResponseDTO(
        Long questionId,
        String questionText,
        List<String> values
) {
}
