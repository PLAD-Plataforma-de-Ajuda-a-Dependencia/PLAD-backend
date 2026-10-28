package plad.dto;

import java.time.Instant;

public record ErrorResponseDTO(
    String message,
    Instant timestamp
) {}
