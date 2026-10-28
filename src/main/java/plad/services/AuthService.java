package plad.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.RegisterRequestDTO;
import plad.dto.UserResponseDTO;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.User;
import plad.entity.UserType;
import plad.exception.EmailAlreadyInUseException;
import plad.exception.InvalidRegistrationException;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final DependentRepository dependentRepository;
    private final PsychologistRepository psychologistRepository;
    private final PasswordEncoder passwordEncoder;

    //constructor injection
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, DependentRepository dependentRepository, PsychologistRepository psychologistRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dependentRepository = dependentRepository;
        this.psychologistRepository = psychologistRepository;

    }

    @Transactional
    public UserResponseDTO register(RegisterRequestDTO request) {
        String email = Emails.normalize(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException("Email already in use");
        }

        if (request.typeUser() == UserType.psicologo) {
            if (request.credentialType() == null || request.crmCrpNumber() == null || request.crmCrpNumber().isBlank()) {
                throw new InvalidRegistrationException("Psychologist must provide credentials");
            }
            if (psychologistRepository.existsByCrmCrpNumber(request.crmCrpNumber())) {
                throw new InvalidRegistrationException("Credential number already registered");
            }
        }

        User user = new User();
        user.setEmail(email);
        user.setRealName(request.realName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setBirthDate(request.birthDate());
        user.setGender(request.gender());
        user.setTypeUser(request.typeUser());
        User savedUser;
        try {
            // flush now so a concurrent registration with the same email hits the unique constraint here and not at commit
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyInUseException("Email already in use");
        }

        if (request.typeUser() == UserType.dependente) {
            Dependent dependent = new Dependent();
            dependent.setUser(savedUser);
            dependentRepository.save(dependent);
        } else {
            Psychologist psychologist = new  Psychologist();
            psychologist.setUser(savedUser);
            psychologist.setCredentialType(request.credentialType());
            psychologist.setCrmCrpNumber(request.crmCrpNumber());
            psychologist.setGraduate(request.graduate());
            try {
                psychologistRepository.saveAndFlush(psychologist);
            } catch (DataIntegrityViolationException e) {
                throw new InvalidRegistrationException("Credential number already registered");
            }
        }

        return new UserResponseDTO(
                savedUser.getId(),
                savedUser.getRealName(),
                savedUser.getEmail()
        );

    }
}
