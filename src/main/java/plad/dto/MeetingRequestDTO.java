package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

// Same body for POST and PUT /meetings. Only the shape is checked here; the limits (duration, capacity, lead time)
// live in MeetingRules so they answer INVALID_MEETING with the offending field.
public record MeetingRequestDTO(
        @Schema(description = "Meeting title", example = "Grupo de apoio semanal", maxLength = 120)
        @NotBlank @Size(max = 120)
        String title,
        @Schema(description = "What the meeting is about; optional", example = "Roda de conversa para quem está no primeiro mês",
                maxLength = 2000, nullable = true)
        @Size(max = 2000)
        String description,
        @Schema(description = "Start (ISO-8601 instant). At least 60 minutes from now when creating",
                example = "2026-11-12T22:00:00Z")
        @NotNull
        Instant startsAt,
        @Schema(description = "End (ISO-8601 instant). Between 15 and 180 minutes after the start",
                example = "2026-11-12T23:00:00Z")
        @NotNull
        Instant endsAt,
        @Schema(description = "Maximum number of participants, 2 to 30", example = "12")
        @NotNull
        Integer capacity
) {}
