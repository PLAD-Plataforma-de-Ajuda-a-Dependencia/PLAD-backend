package plad.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plad.entity.AuditAction;
import plad.entity.AuditLog;
import plad.entity.UserType;
import plad.repositories.AuditLogRepository;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// the guard that keeps the audit log from becoming another copy of the sensitive data (RNF003)
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void recordSavesIdsAndTheRoleOfTheActor() {
        auditService.record(AuditAction.TRIAGE_READ, 5L, UserType.psicologo, 7L, "TRIAGE", 3L, Map.of("answers_count", 4));

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        AuditLog entry = saved.getValue();
        assertThat(entry.getAction()).isEqualTo(AuditAction.TRIAGE_READ);
        assertThat(entry.getActorId()).isEqualTo(5L);
        assertThat(entry.getSubjectUserId()).isEqualTo(7L);
        assertThat(entry.getEntity()).isEqualTo("TRIAGE");
        assertThat(entry.getEntityId()).isEqualTo(3L);
        assertThat(entry.getDetails()).containsEntry("actor_role", "psicologo").containsEntry("answers_count", 4);
    }

    @Test
    void recordAcceptsStateChangeLabelsAndFlags() {
        auditService.record(AuditAction.TRIAGE_RESUBMITTED, 7L, UserType.dependente, 7L, "TRIAGE", 3L,
                Map.of("from_state", "pending", "to_state", "reviewed", "replaced", true));

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        assertThat(saved.getValue().getDetails()).containsEntry("from_state", "pending").containsEntry("replaced", true);
    }

    @Test
    void recordWithoutDetailsOrRoleStoresNullDetails() {
        auditService.record(AuditAction.TRIAGE_SUBMITTED, null, null, 7L, "TRIAGE", 3L, null);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(saved.capture());
        assertThat(saved.getValue().getDetails()).isNull();
        assertThat(saved.getValue().getActorId()).isNull();
    }

    @Test
    void freeTextUnderAnyOtherKeyIsRejectedAndNotSaved() {
        assertRejected(Map.of("note", "quero parar de beber"));
        assertRejected(Map.of("answers", "AGE_18_24"));
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void textThatIsNotAStateLabelIsRejectedEvenUnderAnAllowedKey() {
        assertRejected(Map.of("to_state", "Maria da Silva"));
        assertRejected(Map.of("from_state", "a@b.com"));
        assertRejected(Map.of("to_state", "x".repeat(31)));
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void nullValuesCollectionsAndOddKeysAreRejected() {
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("answers_count", null);
        assertRejected(withNull);
        assertRejected(Map.of("answers", java.util.List.of("AGE_18_24")));
        assertRejected(Map.of("Texto Livre", 1));
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void rejectionDoesNotEchoTheOffendingValue() {
        assertThatThrownBy(() -> auditService.record(AuditAction.TRIAGE_READ, 1L, UserType.admin, 2L, "TRIAGE", 3L,
                Map.of("note", "valor-sensivel-9999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("valor-sensivel-9999");
    }

    private void assertRejected(Map<String, Object> details) {
        assertThatThrownBy(() -> auditService.record(AuditAction.TRIAGE_READ, 1L, UserType.admin, 2L, "TRIAGE", 3L, details))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
