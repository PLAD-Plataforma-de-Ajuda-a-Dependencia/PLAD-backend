package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponseDTO(
        @Schema(description = "User id", example = "1")
        Long id,
        @Schema(description = "Full name", example = "Maria Silva")
        String realName,
        @Schema(description = "Normalized (lowercase) e-mail", example = "maria@example.com")
        String email
) {
}
