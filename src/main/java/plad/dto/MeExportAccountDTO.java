package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import plad.entity.Sex;
import plad.entity.UserType;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "The account data every type has. The password hash is never exported")
public record MeExportAccountDTO(
        @Schema(description = "User id", example = "1")
        Long id,
        @Schema(description = "Normalized (lowercase) e-mail", example = "maria@example.com")
        String email,
        @Schema(description = "Account type", example = "dependente")
        UserType typeUser,
        @Schema(description = "Birth date given at registration", example = "1990-05-20")
        LocalDate birthDate,
        @Schema(description = "Gender given at registration", example = "feminino")
        Sex gender,
        @Schema(description = "When the account was created (UTC)", example = "2026-10-27T21:15:30Z")
        Instant createdAt
) {
}
