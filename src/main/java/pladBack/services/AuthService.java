package pladBack.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pladBack.DTO.RegisterRequestDTO;
import pladBack.DTO.UserResponseDTO;
import pladBack.entity.Dependent;
import pladBack.entity.Psychologist;
import pladBack.entity.User;
import pladBack.entity.userType;
import pladBack.exception.EmailAlreadyInUseException;
import pladBack.exception.InvalidRegistrationException;
import pladBack.repositories.dependentRepository;
import pladBack.repositories.psychologistRepository;
import pladBack.repositories.userRepository;

@Service
public class AuthService {

    private final userRepository userRepository;
    private final dependentRepository dependentRepository;
    private final psychologistRepository psychologistRepository;
    private final PasswordEncoder passwordEncoder;

    //constructor injection
    public AuthService(userRepository userRepository, PasswordEncoder passwordEncoder, dependentRepository dependentRepository, psychologistRepository psychologistRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dependentRepository = dependentRepository;
        this.psychologistRepository = psychologistRepository;

    }

    @Transactional
    public UserResponseDTO register(RegisterRequestDTO request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException("Email already in use");
        }

        if (request.typeUser() == userType.psicologo) {
            if (request.credentialType() == null || request.crmCrpNumber() == null){
                throw new InvalidRegistrationException("Psychologist must provide credentials");
            }
            if (psychologistRepository.findByCrmCrpNumber(request.crmCrpNumber()).isPresent()) {
                throw new InvalidRegistrationException("Credential number already registered");
            }
        }

        User user = new User();
        user.setEmail(request.email());
        user.setReal_name(request.realName());
        user.setPassword_hash(passwordEncoder.encode(request.password()));
        user.setBirth_date(request.birthDate());
        user.setGender(request.gender());
        user.setTypeUser(request.typeUser());
        User saveduser = userRepository.save(user);

        if (request.typeUser() == userType.dependente) {
            Dependent dependent = new Dependent();
            dependent.setUser(saveduser);
            dependentRepository.save(dependent);
        } else {
            Psychologist psychologist = new  Psychologist();
            psychologist.setUser(saveduser);
            psychologist.setCredentialType(request.credentialType());
            psychologist.setCrmCrpNumber(request.crmCrpNumber());
            psychologist.setGraduate(request.graduate());
            psychologistRepository.save(psychologist);
        }

        return new UserResponseDTO(
                saveduser.getId(),
                saveduser.getReal_name(),
                saveduser.getEmail()
        );

    }
}
