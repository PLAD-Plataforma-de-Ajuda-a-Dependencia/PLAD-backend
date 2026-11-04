package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameResponseDTO(
        @Schema(description = "The dependent's new anonymous nickname", example = "Coruja Serena")
        String nickname
) {
}
