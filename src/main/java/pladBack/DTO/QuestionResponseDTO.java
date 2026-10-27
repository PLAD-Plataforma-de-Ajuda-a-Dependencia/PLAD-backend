package pladBack.DTO;

import pladBack.entity.QuestionType;

import java.util.List;

public record QuestionResponseDTO(
        Long id,
        String questionText,
        QuestionType questionType,
        List<OptionDTO> options,
        short displayOrder,
        boolean required
) {
}
