package plad.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import plad.config.TestcontainersConfig;
import plad.entity.AuditAction;
import plad.entity.UserType;
import plad.repositories.AuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// same annotations as the other full-context tests so the Spring context is reused (see CLAUDE.md on connections)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuditServiceTransactionTest {

    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditLogRepository auditLogRepository;

    // no @Transactional on purpose: an entry must never be written apart from the action it describes
    @Test
    void recordOutsideATransactionFailsAndWritesNothing() {
        long before = auditLogRepository.count();

        assertThatThrownBy(() -> auditService.record(AuditAction.TRIAGE_SUBMITTED, 1L, UserType.dependente, 1L, "TRIAGE", 1L, null))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(auditLogRepository.count()).isEqualTo(before);
    }
}
