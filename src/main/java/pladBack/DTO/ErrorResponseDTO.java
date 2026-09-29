package pladBack.DTO;

import java.time.Instant;

public record ErrorResponseDTO(
    String message,
    Instant timestamp
) {}
