package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import plad.validation.MaxBytes;

public record DeleteAccountRequestDTO(
        @Schema(description = "The current password, to confirm that the account holder is the one deleting it",
                example = "senha-segura-123", maxLength = 72)
        @NotBlank @MaxBytes(value = 72) String password
) {
}
