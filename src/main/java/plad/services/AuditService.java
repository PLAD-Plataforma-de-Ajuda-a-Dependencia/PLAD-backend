package plad.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.AccessLogEntryDTO;
import plad.dto.AuditLogResponseDTO;
import plad.dto.PageResponseDTO;
import plad.entity.AuditAction;
import plad.entity.AuditLog;
import plad.entity.UserType;
import plad.repositories.AuditLogRepository;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// Audit trail of access to sensitive data (LGPD art. 11, RNF003). Every read of health data by a third party
// and every write of it goes through record(), inside the transaction of the audited action.
@Service
public class AuditService {

    public static final String ACTOR_ROLE = "actor_role";

    // the only text values details may carry: short lowercase labels of a state, never user content
    private static final Set<String> TEXT_KEYS = Set.of(ACTOR_ROLE, "from_state", "to_state");
    private static final Pattern LABEL = Pattern.compile("[a-z_]{1,30}");
    private static final Pattern KEY = Pattern.compile("[a-z_]{1,40}");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
    private static final List<AuditAction> THIRD_PARTY_READS = Arrays.stream(AuditAction.values())
            .filter(AuditAction::isThirdPartyRead)
            .toList();

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // MANDATORY: calling it without a transaction fails, so the entry is committed (or rolled back)
    // together with the action it describes. details takes ids, numbers, booleans and state labels only;
    // anything else (answers, names, e-mails, free text) is rejected.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, Long actorId, UserType actorRole, Long subjectUserId,
                       String entity, Long entityId, Map<String, Object> extraDetails) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (actorRole != null) {
            details.put(ACTOR_ROLE, actorRole.name());
        }
        if (extraDetails != null) {
            details.putAll(extraDetails);
        }
        requireNoContent(details);

        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setActorId(actorId);
        entry.setSubjectUserId(subjectUserId);
        entry.setEntity(entity);
        entry.setEntityId(entityId);
        entry.setDetails(details.isEmpty() ? null : details);
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<AuditLogResponseDTO> findPage(Long subjectUserId, AuditAction action, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<AuditLog> result;
        if (subjectUserId != null && action != null) {
            result = auditLogRepository.findBySubjectUserIdAndAction(subjectUserId, action, pageable);
        } else if (subjectUserId != null) {
            result = auditLogRepository.findBySubjectUserId(subjectUserId, pageable);
        } else if (action != null) {
            result = auditLogRepository.findByAction(action, pageable);
        } else {
            result = auditLogRepository.findAll(pageable);
        }
        return PageResponseDTO.of(result, AuditLogResponseDTO::from);
    }

    // what the data subject may see: when someone else read their data and in which role
    @Transactional(readOnly = true)
    public PageResponseDTO<AccessLogEntryDTO> findAccessLog(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        return PageResponseDTO.of(
                auditLogRepository.findBySubjectUserIdAndActionIn(userId, THIRD_PARTY_READS, pageable),
                AccessLogEntryDTO::from);
    }

    private static void requireNoContent(Map<String, Object> details) {
        for (Map.Entry<String, Object> detail : details.entrySet()) {
            String key = detail.getKey();
            Object value = detail.getValue();
            if (key == null || !KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Invalid audit detail key");
            }
            boolean allowed = value instanceof Number || value instanceof Boolean
                    || (value instanceof String text && TEXT_KEYS.contains(key) && LABEL.matcher(text).matches());
            if (!allowed) {
                // never echo the value: it may be exactly the content the log must not hold
                throw new IllegalArgumentException("Audit detail '" + key + "' may only hold an id, a number, a flag or a state label");
            }
        }
    }
}
