package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import java.time.Instant;
import java.util.List;

// a dependent is identified by nickname only: the real name never leaves the account except to psychologists
@Schema(description = "Dependent-specific data")
public record MeExportDependentDTO(
        @Schema(description = "The anonymous nickname in use now", example = "Morcego Sereno")
        String nickname,
        @Schema(description = "When the dependent profile was created (UTC)", example = "2026-10-27T21:15:30Z")
        Instant createdAt,
        @ArraySchema(arraySchema = @Schema(description = "Every nickname the dependent had, oldest first"))
        List<MeExportNicknameDTO> nicknameHistory
) {
}
