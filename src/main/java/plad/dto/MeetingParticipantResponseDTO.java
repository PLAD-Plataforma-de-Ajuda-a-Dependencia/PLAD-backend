package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// Only the host psychologist gets this (GET /meetings/{id}/participants): the one place besides the triage where a
// dependent's real name is shown, and every read is audited. Listed in RealNameExposureTest.
public record MeetingParticipantResponseDTO(
        @Schema(description = "The opaque identity this dependent has in the meeting's video room (the same one the room "
                + "shows), so the host can match a person on screen to the nickname and real name below, and use it in "
                + "the speak and remove routes. Different in every meeting; it holds no user id",
                example = "q3ZxK9mR1v0nT7bYw2cF8dJ4hA6sLpUe5oGiXkNcVtM")
        String identity,
        @Schema(description = "Anonymous nickname, the name every other user sees", example = "Morcego Sereno")
        String nickname,
        @Schema(description = "Real name, shown only to the psychologist who hosts the meeting", example = "Maria Silva")
        String realName,
        @Schema(description = "When the dependent registered (UTC)", example = "2026-11-06T14:30:00Z")
        Instant registeredAt
) {}
