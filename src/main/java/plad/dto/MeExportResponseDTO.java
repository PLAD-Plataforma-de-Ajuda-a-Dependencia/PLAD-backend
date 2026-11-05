package plad.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// Copy of the data the platform holds about the logged-in user (LGPD art. 18, II and V). The API always sends every
// key: blocks that do not apply to the account type are null.
public record MeExportResponseDTO(
        @Schema(description = "When this export was generated (UTC)", example = "2026-10-27T21:15:30Z")
        Instant exportedAt,
        MeExportAccountDTO account,
        @Schema(description = "Dependents only. Null for other types", nullable = true)
        MeExportDependentDTO dependent,
        @Schema(description = "Psychologists only. Null for other types", nullable = true)
        MeExportPsychologistDTO psychologist,
        @ArraySchema(arraySchema = @Schema(description = "Every acceptance of the terms and the privacy policy, oldest first"))
        List<MeExportConsentDTO> consents,
        @Schema(description = "Dependents only: null for other types and for a dependent that has not submitted the triage",
                nullable = true)
        MeExportTriageDTO triage
) {
}
