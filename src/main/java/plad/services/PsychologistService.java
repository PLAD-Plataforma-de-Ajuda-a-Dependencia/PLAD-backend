package plad.services;

import org.springframework.stereotype.Service;
import plad.entity.Psychologist;
import plad.exception.ForbiddenActionException;
import plad.repositories.PsychologistRepository;

// The one place that decides whether a psychologist may act on the platform (host a meeting today).
// For now it only confirms the user is a psychologist; the CRP/CRM validation (is_validate) enters here later,
// so no caller has to change.
@Service
public class PsychologistService {

    private final PsychologistRepository psychologistRepository;

    public PsychologistService(PsychologistRepository psychologistRepository) {
        this.psychologistRepository = psychologistRepository;
    }

    public Psychologist requireCanAct(Long userId) {
        return psychologistRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenActionException("Only psychologists can do this"));
    }
}
