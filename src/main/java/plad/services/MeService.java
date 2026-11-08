package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.MeResponseDTO;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.User;
import plad.exception.InvalidCredentialsException;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.TriageRepository;
import plad.repositories.UserRepository;

@Service
public class MeService {

    private final UserRepository userRepository;
    private final PsychologistRepository psychologistRepository;
    private final DependentRepository dependentRepository;
    private final TriageRepository triageRepository;
    private final ConsentService consentService;

    public MeService(UserRepository userRepository, PsychologistRepository psychologistRepository,
                     DependentRepository dependentRepository, TriageRepository triageRepository,
                     ConsentService consentService) {
        this.consentService = consentService;
        this.dependentRepository = dependentRepository;
        this.userRepository = userRepository;
        this.psychologistRepository = psychologistRepository;
        this.triageRepository = triageRepository;
    }

    // the type comes from the database, not from the token's role claim, so an old token cannot show the wrong screen
    @Transactional(readOnly = true)
    public MeResponseDTO findMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));
        boolean consentPending = consentService.isPending(userId);
        return switch (user.getTypeUser()) {
            case dependente -> {
                Dependent dependent = dependentRepository.findById(userId)
                        .orElseThrow(() -> new IllegalStateException("Dependent " + userId + " has no dependents row"));
                yield MeResponseDTO.ofDependent(user, triageRepository.existsByDependentUserId(userId), dependent.getNickname(),
                        consentPending, consentService.isHealthPending(userId));
            }
            case psicologo -> {
                Psychologist psychologist = psychologistRepository.findById(userId)
                        .orElseThrow(() -> new IllegalStateException("Psychologist " + userId + " has no psychologists row"));
                yield MeResponseDTO.ofPsychologist(user, psychologist, consentPending);
            }
            case admin -> MeResponseDTO.ofAdmin(user, consentPending);
        };
    }
}
