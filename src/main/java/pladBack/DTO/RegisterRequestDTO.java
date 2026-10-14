package pladBack.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pladBack.entity.userType;
import pladBack.entity.sex;
import pladBack.entity.CrmCrpType;

import java.time.LocalDate;

public record RegisterRequestDTO(
        @NotBlank String realName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotNull LocalDate birthDate,
        @NotNull sex gender,
        @NotNull userType typeUser,
        CrmCrpType credentialType,
        String crmCrpNumber,
        String graduate
) {}