package plad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import plad.validation.MaxBytes;

public record LoginRequestDTO(
   @NotBlank @Size(max = 255) String email,
   @NotBlank @MaxBytes(value = 72) String password
) {}
