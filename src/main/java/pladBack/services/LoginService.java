package pladBack.services;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pladBack.DTO.LoginRequestDTO;
import pladBack.DTO.LoginResponseDTO;
import pladBack.entity.User;
import pladBack.exception.InvalidCredentialsException;
import pladBack.repositories.userRepository;

import java.util.Optional;

@Service
public class LoginService {

    private final userRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private static final String DUMMY_HASH =
            "$2a$10$DowJonesXXXXXXXXXXXXXuY1P0k9y2sZ3wq7vRr8tGZzL4mN5cWe6";

    public LoginService(userRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponseDTO login(LoginRequestDTO request) {

        Optional<User> userOpt = userRepository.findByEmail(request.email());

        String hashToCheck = userOpt
                .map(User::getPassword_hash)
                .orElse(DUMMY_HASH);

        Boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (userOpt.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = userOpt.get();
        String token = jwtService.generateToken(user);

        return new LoginResponseDTO(token);

    }
}
