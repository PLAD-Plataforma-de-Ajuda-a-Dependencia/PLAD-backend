package plad.services;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.AdminPsychologistResponseDTO;
import plad.dto.PageResponseDTO;
import plad.entity.Psychologist;
import plad.exception.ForbiddenActionException;
import plad.repositories.PsychologistRepository;

// The one place that decides whether a psychologist may act on the platform (host a meeting today).
// For now it only confirms the user is a psychologist; the CRP/CRM validation (is_validate) enters here later,
// so no caller has to change.
@Service
public class PsychologistService {

    // sign-up date lives on users; the id breaks ties so two accounts created together never swap between pages
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("user.createdAt"), Sort.Order.desc("userId"));

    private final PsychologistRepository psychologistRepository;

    public PsychologistService(PsychologistRepository psychologistRepository) {
        this.psychologistRepository = psychologistRepository;
    }

    public Psychologist requireCanAct(Long userId) {
        return psychologistRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenActionException("Only psychologists can do this"));
    }

    // admin's list (decision 32): real name and credential, no e-mail. Not health data, so no audit entry.
    @Transactional(readOnly = true)
    public PageResponseDTO<AdminPsychologistResponseDTO> findPageForAdmin(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        return PageResponseDTO.of(psychologistRepository.findAllBy(pageable), psychologist ->
                new AdminPsychologistResponseDTO(psychologist.getUserId(), psychologist.getUser().getRealName(),
                        psychologist.getCredentialType(), psychologist.getCrmCrpNumber(), psychologist.isValidate(),
                        psychologist.getUser().getCreatedAt()));
    }
}
