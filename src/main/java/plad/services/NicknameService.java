package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.entity.AuditAction;
import plad.entity.UserType;
import plad.exception.InvalidCredentialsException;
import plad.repositories.DependentRepository;
import plad.services.nickname.NicknameGenerator;

// Anonymous nicknames of dependents (RF007). A collision (another dependent already has the draw) is settled by
// the unique constraint on dependents.nickname: the INSERT/UPDATE below simply touches no row and the generator
// draws again, so a race between two requests never aborts the transaction.
@Service
public class NicknameService {

    static final String AUDIT_ENTITY = "DEPENDENT";

    private final DependentRepository dependentRepository;
    private final NicknameGenerator generator;
    private final RateLimiterService rateLimiterService;
    private final AuditService auditService;

    public NicknameService(DependentRepository dependentRepository, NicknameGenerator generator,
                           RateLimiterService rateLimiterService, AuditService auditService) {
        this.dependentRepository = dependentRepository;
        this.generator = generator;
        this.rateLimiterService = rateLimiterService;
        this.auditService = auditService;
    }

    // Creates the dependents row for a freshly saved user (the user row must already be flushed) and returns its nickname.
    @Transactional
    public String assignInitial(Long userId) {
        String nickname = generator.claim(candidate -> dependentRepository.insertWithNickname(userId, candidate) == 1);
        dependentRepository.openNicknameHistory(userId, nickname);
        return nickname;
    }

    // Swaps the dependent's nickname for a new one, keeps the history and audits it. At most 5 a day.
    @Transactional
    public String reroll(Long userId) {
        rateLimiterService.consume(RateLimiterService.NICKNAME_REROLL_PREFIX + userId);
        if (!dependentRepository.existsById(userId)) {
            throw new InvalidCredentialsException("User no longer exists");
        }
        String nickname = generator.claim(candidate -> dependentRepository.updateNicknameIfFree(userId, candidate) == 1);
        dependentRepository.closeCurrentNicknameHistory(userId);
        dependentRepository.openNicknameHistory(userId, nickname);
        // the nickname itself never goes into the details: ids and flags only
        auditService.record(AuditAction.NICKNAME_REROLLED, userId, UserType.dependente, userId, AUDIT_ENTITY, userId, null);
        return nickname;
    }
}
