package plad.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import plad.entity.AuditAction;
import plad.entity.AuditLog;

import java.util.Collection;

// one derived query per filter combination: "(:x is null or ...)" fails type inference on PostgreSQL for null parameters
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findBySubjectUserId(Long subjectUserId, Pageable pageable);

    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);

    Page<AuditLog> findBySubjectUserIdAndAction(Long subjectUserId, AuditAction action, Pageable pageable);

    Page<AuditLog> findBySubjectUserIdAndActionIn(Long subjectUserId, Collection<AuditAction> actions, Pageable pageable);
}
