package plad.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.RegisterRequestDTO;
import plad.dto.UserResponseDTO;
import plad.entity.CrmCrpType;
import plad.entity.Psychologist;
import plad.entity.User;
import plad.entity.UserType;
import plad.exception.EmailAlreadyInUseException;
import plad.exception.InvalidRegistrationException;
import plad.repositories.PsychologistRepository;
import plad.repositories.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final NicknameService nicknameService;
    private final PsychologistRepository psychologistRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConsentService consentService;

    //constructor injection
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, NicknameService nicknameService,
                       PsychologistRepository psychologistRepository, ConsentService consentService) {
        this.consentService = consentService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.nicknameService = nicknameService;
        this.psychologistRepository = psychologistRepository;

    }

    @Transactional
    public UserResponseDTO register(RegisterRequestDTO request, String clientIp) {
        String email = Emails.normalize(request.email());

        // the else branch below creates a psychologist, so admin must be turned away before anything is written
        if (request.typeUser() == UserType.admin) {
            throw new InvalidRegistrationException("Admin accounts cannot be self-registered");
        }

        // New psychologists register with a CRP only (decision 33). The CRM label stays in the enum so accounts created
        // before this rule are still read; a static rule, so it is checked before anything touches the database
        if (request.typeUser() == UserType.psicologo && request.credentialType() == CrmCrpType.CRM) {
            throw new InvalidRegistrationException("CRM registrations are not accepted; register with a CRP");
        }

        // LGPD art. 11: no account without the acceptance of the terms and privacy policy in force
        consentService.requireCurrent(request.acceptedTermsVersion(), request.acceptedPrivacyVersion());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException("Email already in use");
        }

        String crmCrpNumber = null;
        if (request.typeUser() == UserType.psicologo) {
            // a number with no letters or digits ("///") normalizes to empty and counts as missing
            if (request.crmCrpNumber() != null) {
                crmCrpNumber = CrmCrpNumbers.normalize(request.crmCrpNumber());
            }
            if (request.credentialType() == null || crmCrpNumber == null || crmCrpNumber.isEmpty()) {
                throw new InvalidRegistrationException("Psychologist must provide credentials");
            }
            if (psychologistRepository.existsByCrmCrpNumber(crmCrpNumber)) {
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

        // same transaction as the user: the acceptance commits or rolls back with the account
        consentService.record(savedUser, clientIp);

        if (request.typeUser() == UserType.dependente) {
            // creates the dependents row together with its random nickname, in this same transaction
            nicknameService.assignInitial(savedUser.getId());
        } else {
            Psychologist psychologist = new  Psychologist();
            psychologist.setUser(savedUser);
            psychologist.setCredentialType(request.credentialType());
            psychologist.setCrmCrpNumber(crmCrpNumber);
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
