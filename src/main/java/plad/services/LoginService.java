package plad.services;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import plad.dto.LoginRequestDTO;
import plad.dto.LoginResponseDTO;
import plad.entity.User;
import plad.exception.InvalidCredentialsException;
import plad.repositories.UserRepository;

import java.util.Optional;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiterService rateLimiterService;

    private static final String DUMMY_HASH =
            "$2a$10$DowJonesXXXXXXXXXXXXXuY1P0k9y2sZ3wq7vRr8tGZzL4mN5cWe6";

    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                        RateLimiterService rateLimiterService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiterService = rateLimiterService;
    }

    public LoginResponseDTO login(LoginRequestDTO request) {

        String email = Emails.normalize(request.email());
        // on top of the per-IP limit, so attempts spread over many IPs still hit one budget per account
        rateLimiterService.consume("login-email:" + email);

        Optional<User> userOpt = userRepository.findByEmail(email);

        String hashToCheck = userOpt
                .map(User::getPasswordHash)
                .orElse(DUMMY_HASH);

        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (userOpt.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = userOpt.get();
        String token = jwtService.generateToken(user);

        return new LoginResponseDTO(token);

    }
}
