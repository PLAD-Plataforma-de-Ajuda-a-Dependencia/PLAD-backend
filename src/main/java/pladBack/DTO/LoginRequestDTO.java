package pladBack.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pladBack.validation.MaxBytes;

public record LoginRequestDTO(
   @NotBlank @Size(max = 255) String email,
   @NotBlank @MaxBytes(value = 72) String password
) {}
