package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A meeting the dependent registered for")
public record MeExportMeetingRegistrationDTO(
        @Schema(description = "Meeting id", example = "42")
        Long meetingId,
        @Schema(description = "Meeting title", example = "Grupo de apoio semanal")
        String title,
        @Schema(description = "When the meeting starts (UTC)", example = "2026-11-12T22:00:00Z")
        Instant startsAt,
        @Schema(description = "When the dependent registered (UTC)", example = "2026-11-06T14:30:00Z")
        Instant registeredAt
) {
}
