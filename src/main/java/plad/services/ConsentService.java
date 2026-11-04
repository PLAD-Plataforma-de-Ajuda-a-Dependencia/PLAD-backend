package plad.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.entity.Consent;
import plad.entity.User;
import plad.exception.ConsentRequiredException;
import plad.exception.InvalidCredentialsException;
import plad.repositories.ConsentRepository;
import plad.repositories.UserRepository;

// LGPD art. 11: the terms and the privacy policy, in the versions currently in force, must be accepted
@Service
public class ConsentService {

    public static final String TERMS = "terms";
    public static final String PRIVACY = "privacy";

    private final ConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final String termsVersion;
    private final String privacyVersion;

    public ConsentService(ConsentRepository consentRepository, UserRepository userRepository,
                          @Value("${consent.terms-version}") String termsVersion,
                          @Value("${consent.privacy-version}") String privacyVersion) {
        if (termsVersion.isBlank() || privacyVersion.isBlank()) {
            throw new IllegalStateException("consent.terms-version and consent.privacy-version must not be blank");
        }
        this.consentRepository = consentRepository;
        this.userRepository = userRepository;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
    }

    // a missing version counts as not accepted; the message names the versions in force, which are public
    public void requireCurrent(String acceptedTermsVersion, String acceptedPrivacyVersion) {
        if (!termsVersion.equals(acceptedTermsVersion) || !privacyVersion.equals(acceptedPrivacyVersion)) {
            throw new ConsentRequiredException("The terms (version " + termsVersion + ") and the privacy policy (version "
                    + privacyVersion + ") in force must be accepted");
        }
    }

    // joins the caller's transaction, so the consent commits or rolls back with the action that needed it
    @Transactional
    public void record(User user, String ip) {
        recordOne(user, TERMS, termsVersion, ip);
        recordOne(user, PRIVACY, privacyVersion, ip);
    }

    // POST /me/consents: the user comes from the token, so one that no longer exists is a 401 like in GET /me
    @Transactional
    public void accept(Long userId, String acceptedTermsVersion, String acceptedPrivacyVersion, String ip) {
        requireCurrent(acceptedTermsVersion, acceptedPrivacyVersion);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));
        record(user, ip);
    }

    @Transactional(readOnly = true)
    public boolean isPending(Long userId) {
        return !consentRepository.existsByUserIdAndDocumentAndVersion(userId, TERMS, termsVersion)
                || !consentRepository.existsByUserIdAndDocumentAndVersion(userId, PRIVACY, privacyVersion);
    }

    // accepting a version that is already on file adds nothing, so repeating the call is harmless
    private void recordOne(User user, String document, String version, String ip) {
        if (consentRepository.existsByUserIdAndDocumentAndVersion(user.getId(), document, version)) {
            return;
        }
        Consent consent = new Consent();
        consent.setUser(user);
        consent.setDocument(document);
        consent.setVersion(version);
        consent.setIp(ip);
        consentRepository.save(consent);
    }
}
