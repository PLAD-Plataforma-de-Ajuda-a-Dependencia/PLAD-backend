package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import plad.validation.MaxBytes;

public record LoginRequestDTO(
   @Schema(description = "Login e-mail; case and surrounding spaces are ignored", example = "maria@example.com")
   @NotBlank @Size(max = 255) String email,
   @Schema(description = "At most 72 bytes in UTF-8", example = "senha-segura-123", maxLength = 72)
   @NotBlank @MaxBytes(value = 72) String password
) {}
