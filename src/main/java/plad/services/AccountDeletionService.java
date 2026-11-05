package plad.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.entity.User;
import plad.entity.UserType;
import plad.exception.ForbiddenActionException;
import plad.exception.InvalidCredentialsException;
import plad.repositories.UserRepository;

// Right to erasure (LGPD art. 18, VI): removes the user and every row that hangs from it in one transaction.
// The subtype, triage and answers, consents, nickname history and refresh tokens go through ON DELETE CASCADE (V24).
// audit_log has no FK to users and accepts no deletes: it keeps ids only, as proof that accesses happened.
// The sessions end with the rows: the refresh tokens are gone, and a user that no longer exists makes every access
// token invalid (JwtAuthenticationFilter), so there is nothing left to revoke.
@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiterService rateLimiterService;

    public AccountDeletionService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  RateLimiterService rateLimiterService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiterService = rateLimiterService;
    }

    @Transactional
    public void delete(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));
        // an admin cannot erase itself through the API: the last one would lock everyone out of /admin
        if (user.getTypeUser() == UserType.admin) {
            throw new ForbiddenActionException("An admin account cannot be deleted through the API");
        }

        // the password is checked with the same per-account budget as login, so a stolen access token cannot be used
        // to guess it; the slot comes back on a correct password (only failures count)
        String emailKey = RateLimiterService.LOGIN_EMAIL_PREFIX + Emails.normalize(user.getEmail());
        rateLimiterService.consume(emailKey);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Wrong password");
        }
        rateLimiterService.refund(emailKey);

        userRepository.deleteAccountById(userId);
    }
}
