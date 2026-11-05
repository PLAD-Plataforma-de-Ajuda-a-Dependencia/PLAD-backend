package plad.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.ChangePasswordRequestDTO;
import plad.entity.User;
import plad.exception.InvalidCredentialsException;
import plad.exception.InvalidPasswordChangeException;
import plad.repositories.UserRepository;

@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService userSessionService;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiterService rateLimiterService;

    public PasswordChangeService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                 UserSessionService userSessionService, RefreshTokenService refreshTokenService,
                                 RateLimiterService rateLimiterService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userSessionService = userSessionService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiterService = rateLimiterService;
    }

    // Saves the new hash, ends every session of the user and starts a new one for this request, all in one transaction.
    @Transactional
    public RefreshTokenService.Session change(Long userId, ChangePasswordRequestDTO request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        // Same budget as LoginService (per normalized e-mail), so this route is no way to guess passwords without a
        // limit. The slot is taken before the check (an exhausted budget answers 429 without revealing whether the
        // password was right) and handed back when the current password is correct, so only failures count.
        String emailKey = RateLimiterService.LOGIN_EMAIL_PREFIX + Emails.normalize(user.getEmail());
        rateLimiterService.consume(emailKey);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }
        rateLimiterService.refund(emailKey);

        // only reached by someone who knows the current password, so this reveals nothing to a guesser
        if (request.newPassword().equals(request.currentPassword())) {
            throw new InvalidPasswordChangeException("The new password must be different from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);

        userSessionService.revokeAllSessions(userId);

        // revokeAllSessions bumped token_version and cleared the persistence context: reload, so the new access token
        // carries the new version; the refresh token is created after the revocation, so it stays valid
        User refreshed = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));
        return refreshTokenService.start(refreshed);
    }
}
