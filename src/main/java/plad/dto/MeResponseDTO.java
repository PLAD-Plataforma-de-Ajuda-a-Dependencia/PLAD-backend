package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import plad.entity.CrmCrpType;
import plad.entity.Psychologist;
import plad.entity.User;
import plad.entity.UserType;

import java.time.Instant;

// the API always sends every key: fields that do not apply to the user's type are null
public record MeResponseDTO(
        @Schema(description = "User id", example = "1")
        Long id,
        @Schema(description = "Normalized (lowercase) e-mail", example = "maria@example.com")
        String email,
        @Schema(description = "Account type", example = "dependente")
        UserType typeUser,
        @Schema(description = "When the account was created (UTC)", example = "2026-10-27T21:15:30Z")
        Instant createdAt,
        @Schema(description = "Dependents only: whether the triage was already submitted. Null for other types",
                example = "false", nullable = true)
        Boolean hasTriage,
        @Schema(description = "Psychologists only: full name. Null for other types", example = "Maria Silva", nullable = true)
        String realName,
        @Schema(description = "Psychologists only: CRM or CRP. Null for other types", example = "CRP", nullable = true)
        CrmCrpType credentialType,
        @Schema(description = "Psychologists only: normalized credential number. Null for other types",
                example = "06123456", nullable = true)
        String crmCrpNumber,
        @Schema(description = "Psychologists only: whether the credential was validated. Null for other types",
                example = "false", nullable = true)
        Boolean isValidated
) {

    public static MeResponseDTO ofDependent(User user, boolean hasTriage) {
        return new MeResponseDTO(user.getId(), user.getEmail(), user.getTypeUser(), user.getCreatedAt(),
                hasTriage, null, null, null, null);
    }

    public static MeResponseDTO ofPsychologist(User user, Psychologist psychologist) {
        return new MeResponseDTO(user.getId(), user.getEmail(), user.getTypeUser(), user.getCreatedAt(),
                null, user.getRealName(), psychologist.getCredentialType(), psychologist.getCrmCrpNumber(),
                psychologist.isValidate());
    }

    public static MeResponseDTO ofAdmin(User user) {
        return new MeResponseDTO(user.getId(), user.getEmail(), user.getTypeUser(), user.getCreatedAt(),
                null, null, null, null, null);
    }
}
