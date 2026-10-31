package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import plad.entity.AuditAction;
import plad.entity.AuditLog;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponseDTO(
        @Schema(description = "Audit entry id", example = "1")
        Long id,
        @Schema(description = "When the action happened (UTC)", example = "2026-10-27T21:15:30Z")
        Instant occurredAt,
        @Schema(description = "User who acted; null for system actions", example = "7", nullable = true)
        Long actorId,
        @Schema(description = "What happened", example = "TRIAGE_SUBMITTED")
        AuditAction action,
        @Schema(description = "User whose data was affected", example = "7", nullable = true)
        Long subjectUserId,
        @Schema(description = "Kind of record involved", example = "TRIAGE", nullable = true)
        String entity,
        @Schema(description = "Id of the record involved", example = "3", nullable = true)
        Long entityId,
        @Schema(description = "Only ids and state changes (e.g. actor_role); never answers, names, e-mails or free text",
                example = "{\"actor_role\": \"dependente\"}", nullable = true)
        Map<String, Object> details
) {

    public static AuditLogResponseDTO from(AuditLog log) {
        return new AuditLogResponseDTO(log.getId(), log.getOccurredAt(), log.getActorId(), log.getAction(),
                log.getSubjectUserId(), log.getEntity(), log.getEntityId(), log.getDetails());
    }
}
