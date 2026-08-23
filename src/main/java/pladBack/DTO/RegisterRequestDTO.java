package pladBack.DTO;

import pladBack.entity.userType;
import pladBack.entity.sex;
import java.time.LocalDate;

public record RegisterRequestDTO(
        String real_name,
        String email,
        String password,
        LocalDate birthDate,
        sex gender,
        userType typeUser
) {}
