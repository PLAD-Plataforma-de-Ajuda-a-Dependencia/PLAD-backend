package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import plad.entity.UserType;
import plad.entity.Sex;
import plad.entity.CrmCrpType;
import plad.validation.MaxBytes;
import plad.validation.MinAge;

import java.time.LocalDate;

// max sizes mirror the column lengths; password max is BCrypt's 72-byte input limit
public record RegisterRequestDTO(
        @Schema(description = "Full name", example = "Maria Silva")
        @NotBlank @Size(max = 150) String realName,
        @Schema(description = "Login e-mail; stored lowercased and unique", example = "maria@example.com")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(description = "At least 8 characters and at most 72 bytes in UTF-8", example = "senha-segura-123")
        @NotBlank @Size(min = 8) @MaxBytes(value = 72) String password,
        @Schema(description = "Date of birth (yyyy-MM-dd). The platform is only for adults: the person must be at least "
                + "18 years old on today's date in America/Sao_Paulo, turning 18 on the same day and month of birth "
                + "(born on Feb 29: Mar 1 in non-leap years), and at most 120. Otherwise 400 VALIDATION_ERROR on birth_date",
                example = "1990-05-20")
        @NotNull @MinAge(value = 18) LocalDate birthDate,
        @NotNull Sex gender,
        @Schema(description = "Account type; psicologo also requires credential_type and crm_crp_number")
        @NotNull UserType typeUser,
        @Schema(description = "Professional council; required when type_user is psicologo", example = "CRP")
        CrmCrpType credentialType,
        @Schema(description = "Council registration number, unique; required when type_user is psicologo", example = "06/123456")
        @Size(max = 30) String crmCrpNumber,
        @Schema(description = "Graduation, only stored for psicologo", example = "Psicologia - USP")
        @Size(max = 150) String graduate
) {}
