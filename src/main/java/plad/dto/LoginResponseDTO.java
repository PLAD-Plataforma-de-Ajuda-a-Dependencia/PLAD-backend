package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponseDTO(
        @Schema(description = "Access JWT for the Authorization: Bearer header, valid for 15 minutes "
                + "(jwt.expiration-ms); keep it in memory only and renew it with POST /auth/refresh",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.c2lnbmF0dXJl")
        String token
) {}
