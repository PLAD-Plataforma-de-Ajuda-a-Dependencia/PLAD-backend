package pladBack.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import pladBack.entity.userType;
import pladBack.entity.sex;
import pladBack.entity.CrmCrpType;
import pladBack.validation.MaxBytes;

import java.time.LocalDate;

// max sizes mirror the column lengths; password max is BCrypt's 72-byte input limit
public record RegisterRequestDTO(
        @NotBlank @Size(max = 150) String realName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8) @MaxBytes(value = 72) String password,
        @NotNull @Past LocalDate birthDate,
        @NotNull sex gender,
        @NotNull userType typeUser,
        CrmCrpType credentialType,
        @Size(max = 30) String crmCrpNumber,
        @Size(max = 150) String graduate
) {}
