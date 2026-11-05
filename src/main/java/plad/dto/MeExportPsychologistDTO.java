package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import plad.entity.CrmCrpType;

import java.time.Instant;

// a psychologist's own data, returned to that psychologist only
@Schema(description = "Psychologist-specific data")
public record MeExportPsychologistDTO(
        @Schema(description = "Full name given at registration", example = "Maria Silva")
        String realName,
        @Schema(description = "CRM or CRP", example = "CRP")
        CrmCrpType credentialType,
        @Schema(description = "Normalized credential number", example = "06123456")
        String crmCrpNumber,
        @Schema(description = "Graduation given at registration", example = "Psicologia", nullable = true)
        String graduate,
        @Schema(description = "Whether the credential was validated", example = "false")
        boolean isValidated,
        @Schema(description = "When the validation state was last set (UTC)", example = "2026-10-27T21:15:30Z", nullable = true)
        Instant validatedAt
) {
}
