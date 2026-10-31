package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.MeResponseDTO;
import plad.entity.Psychologist;
import plad.entity.User;
import plad.exception.InvalidCredentialsException;
import plad.repositories.PsychologistRepository;
import plad.repositories.TriageRepository;
import plad.repositories.UserRepository;

@Service
public class MeService {

    private final UserRepository userRepository;
    private final PsychologistRepository psychologistRepository;
    private final TriageRepository triageRepository;

    public MeService(UserRepository userRepository, PsychologistRepository psychologistRepository,
                     TriageRepository triageRepository) {
        this.userRepository = userRepository;
        this.psychologistRepository = psychologistRepository;
        this.triageRepository = triageRepository;
    }

    // the type comes from the database, not from the token's role claim, so an old token cannot show the wrong screen
    @Transactional(readOnly = true)
    public MeResponseDTO findMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));
        return switch (user.getTypeUser()) {
            case dependente -> MeResponseDTO.ofDependent(user, triageRepository.existsByDependentUserId(userId));
            case psicologo -> {
                Psychologist psychologist = psychologistRepository.findById(userId)
                        .orElseThrow(() -> new IllegalStateException("Psychologist " + userId + " has no psychologists row"));
                yield MeResponseDTO.ofPsychologist(user, psychologist);
            }
            case admin -> MeResponseDTO.ofAdmin(user);
        };
    }
}
