package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import plad.entity.MeetingStatus;

import java.time.Instant;

// Visible to every authenticated role. The host is the psychologist's name and council registration (decision 23);
// never the e-mail.
public record MeetingResponseDTO(
        @Schema(description = "Meeting id", example = "42")
        Long id,
        @Schema(description = "Meeting title", example = "Grupo de apoio semanal")
        String title,
        @Schema(description = "What the meeting is about; null when none was given", nullable = true)
        String description,
        @Schema(description = "Start (UTC)", example = "2026-11-12T22:00:00Z")
        Instant startsAt,
        @Schema(description = "End (UTC)", example = "2026-11-12T23:00:00Z")
        Instant endsAt,
        @Schema(description = "Maximum number of participants", example = "12")
        int capacity,
        @Schema(description = "scheduled, cancelled (the host cancelled it) or ended (the host closed it)")
        MeetingStatus status,
        @Schema(description = "The psychologist who hosts the meeting: name and council registration, so a dependent "
                + "can check who leads it")
        MeetingHostDTO host,
        @Schema(description = "True when the caller is the host and can edit, cancel or end it", example = "false")
        boolean ownedByMe,
        @Schema(description = "How many dependents are registered. Every role sees only this number; who they are is "
                + "for the host (GET /meetings/{id}/participants)", example = "7")
        int registeredCount,
        @Schema(description = "True when the caller is a dependent registered in this meeting; always false for "
                + "psychologists and admins", example = "false")
        boolean registeredByMe
) {}
