package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import plad.validation.MaxBytes;

public record ChangePasswordRequestDTO(
        @Schema(description = "The password in use now; at most 72 bytes in UTF-8", example = "senha-segura-123", maxLength = 72)
        @NotBlank @MaxBytes(value = 72) String currentPassword,
        @Schema(description = "At least 8 characters and at most 72 bytes in UTF-8, and different from the current one",
                example = "outra-senha-456")
        @NotBlank @Size(min = 8) @MaxBytes(value = 72) String newPassword
) {}
