package pladBack.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pladBack.DTO.RegisterRequestDTO;
import pladBack.DTO.UserResponseDTO;
import pladBack.entity.User;
import pladBack.exception.EmailAlreadyInUseException;
import pladBack.repositories.userRepository;

@Service
public class AuthService {

    private final userRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    //constructor injection
    public AuthService(userRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponseDTO register(RegisterRequestDTO request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException("Email already in use");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setReal_name(request.real_name());
        user.setPassword_hash(passwordEncoder.encode(request.password()));
        user.setBirth_date(request.birthDate());
        user.setGender(request.gender());
        user.setTypeUser(request.typeUser());

        User saveduser = userRepository.save(user);

        return new UserResponseDTO(
                saveduser.getId(),
                saveduser.getReal_name(),
                saveduser.getEmail()
        );

    }
}
