package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MeExportConsentDTO(
        @Schema(description = "Which document was accepted: terms, privacy, health (dependents) or participation (dependents, "
                + "at a registration for a meeting)", example = "terms")
        String document,
        @Schema(description = "Version accepted", example = "1.0")
        String version,
        @Schema(description = "When it was accepted (UTC)", example = "2026-10-27T21:15:30Z")
        Instant acceptedAt,
        @Schema(description = "Client IP recorded at the acceptance", example = "203.0.113.7", nullable = true)
        String ip
) {
}
