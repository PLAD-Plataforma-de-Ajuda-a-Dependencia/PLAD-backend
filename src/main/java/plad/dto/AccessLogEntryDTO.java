package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import plad.entity.AuditAction;
import plad.entity.AuditLog;
import plad.services.AuditService;

import java.time.Instant;

// shows the role of who read the data, never the person (LGPD art. 18)
public record AccessLogEntryDTO(
        @Schema(description = "When the data was accessed (UTC)", example = "2026-10-27T21:15:30Z")
        Instant occurredAt,
        @Schema(description = "What happened", example = "TRIAGE_VIEWED")
        AuditAction action,
        @Schema(description = "Role of who accessed the data: psicologo or admin", example = "psicologo", nullable = true)
        String actorRole
) {

    public static AccessLogEntryDTO from(AuditLog log) {
        Object role = log.getDetails() == null ? null : log.getDetails().get(AuditService.ACTOR_ROLE);
        return new AccessLogEntryDTO(log.getOccurredAt(), log.getAction(), role == null ? null : role.toString());
    }
}
