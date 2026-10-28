package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponseDTO(
        @Schema(description = "JWT for the Authorization: Bearer header",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.c2lnbmF0dXJl")
        String token
) {}
