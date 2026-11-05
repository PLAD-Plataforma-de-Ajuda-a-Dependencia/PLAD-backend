package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MeExportNicknameDTO(
        @Schema(description = "A nickname the dependent had", example = "Morcego Sereno")
        String nickname,
        @Schema(description = "When it was assigned (UTC)", example = "2026-10-27T21:15:30Z")
        Instant assignedAt,
        @Schema(description = "When it was replaced (UTC); null for the nickname in use now",
                example = "2026-10-28T10:00:00Z", nullable = true)
        Instant replacedAt
) {
}
