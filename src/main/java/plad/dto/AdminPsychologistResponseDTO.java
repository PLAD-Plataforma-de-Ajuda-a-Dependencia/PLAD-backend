package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import plad.entity.CrmCrpType;

import java.time.Instant;

// A psychologist as the admin sees them in the management list (decision 32): real name and credential, never the
// e-mail, and nothing about any dependent.
@Schema(description = "A psychologist in the admin's list")
public record AdminPsychologistResponseDTO(
        @Schema(description = "User id of the psychologist", example = "7")
        Long id,
        @Schema(description = "Full name given at registration", example = "Maria Silva")
        String realName,
        @Schema(description = "CRM or CRP", example = "CRP")
        CrmCrpType credentialType,
        @Schema(description = "Normalized credential number", example = "06123456")
        String crmCrpNumber,
        @Schema(description = "Whether the credential was validated", example = "false")
        boolean isValidated,
        @Schema(description = "When the account was created (UTC)", example = "2026-10-27T21:15:30Z")
        Instant createdAt
) {
}
