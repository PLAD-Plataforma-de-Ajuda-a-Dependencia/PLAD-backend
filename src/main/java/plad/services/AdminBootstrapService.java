package plad.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

// creates the first admin from the environment; admins cannot register through /auth/register
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
    // same password rule as the registration: at least 8 characters, at most 72 bytes (BCrypt's input limit)
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_BYTES = 72;
    // users requires these columns and an admin has no personal data to give
    private static final String ADMIN_NAME = "Administrador";
    private static final LocalDate ADMIN_BIRTH_DATE = LocalDate.of(2000, 1, 1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // does nothing when either value is missing or the e-mail is already registered (an existing account is never
    // promoted nor has its password changed); a weak password stops the startup instead of creating an easy admin
    public void createIfAbsent(String rawEmail, String rawPassword) {
        if (rawEmail == null || rawEmail.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return;
        }
        String email = Emails.normalize(rawEmail);
        if (userRepository.existsByEmail(email)) {
            log.info("First admin skipped: the ADMIN_EMAIL account already exists");
            return;
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("ADMIN_PASSWORD must have at least " + MIN_PASSWORD_LENGTH
                    + " characters and at most " + MAX_PASSWORD_BYTES + " bytes in UTF-8");
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setRealName(ADMIN_NAME);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setBirthDate(ADMIN_BIRTH_DATE);
        admin.setGender(Sex.outro);
        admin.setTypeUser(UserType.admin);
        try {
            userRepository.saveAndFlush(admin);
            log.info("First admin created");
        } catch (DataIntegrityViolationException e) {
            // another instance starting at the same time won the race for the unique e-mail
            log.info("First admin skipped: created concurrently by another instance");
        }
    }
}
