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
        @Schema(description = "Dependents only: the anonymous nickname other users see instead of the real name. Null for other types",
                example = "Morcego Sereno", nullable = true)
        String nickname,
        @Schema(description = "Psychologists only: full name. Null for other types", example = "Maria Silva", nullable = true)
        String realName,
        @Schema(description = "Psychologists only: CRM or CRP. Null for other types", example = "CRP", nullable = true)
        CrmCrpType credentialType,
        @Schema(description = "Psychologists only: normalized credential number. Null for other types",
                example = "06123456", nullable = true)
        String crmCrpNumber,
        @Schema(description = "Psychologists only: whether the credential was validated. Null for other types",
                example = "false", nullable = true)
        Boolean isValidated,
        @Schema(description = "True while the user has not accepted the terms and the privacy policy in the versions in force "
                + "(accept with POST /me/consents). Set for every account type", example = "false")
        boolean consentPending,
        @Schema(description = "Dependents only: true while the consent to process health data, in the version in force, is not on "
                + "file (accept with POST /me/consents/health). Without it POST /triage, PUT /triage and the registration for a "
                + "meeting answer 400 CONSENT_REQUIRED. Null for other types", example = "false", nullable = true)
        Boolean healthConsentPending
) {

    public static MeResponseDTO ofDependent(User user, boolean hasTriage, String nickname, boolean consentPending,
                                            boolean healthConsentPending) {
        return new MeResponseDTO(user.getId(), user.getEmail(), user.getTypeUser(), user.getCreatedAt(),
                hasTriage, nickname, null, null, null, null, consentPending, healthConsentPending);
    }

    public static MeResponseDTO ofPsychologist(User user, Psychologist psychologist, boolean consentPending) {
        return new MeResponseDTO(user.getId(), user.getEmail(), user.getTypeUser(), user.getCreatedAt(),
                null, null, user.getRealName(), psychologist.getCredentialType(), psychologist.getCrmCrpNumber(),
                psychologist.isValidate(), consentPending, null);
    }

    public static MeResponseDTO ofAdmin(User user, boolean consentPending) {
        return new MeResponseDTO(user.getId(), user.getEmail(), user.getTypeUser(), user.getCreatedAt(),
                null, null, null, null, null, null, consentPending, null);
    }
}
