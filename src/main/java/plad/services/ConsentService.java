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

import java.time.Instant;

// LGPD art. 11: four documents, each accepted in the version currently in force.
// - terms and privacy: accepted at sign-up and again on a new version; they block nothing (decision 16)
// - health: dependents only, in its own step before the first triage; POST/PUT /triage and the registration for a
//   meeting refuse without it; the dependent can revoke it (DELETE /me/consents/health), which also erases the triage
//   and the registrations for meetings that have not started (decision 35)
// - participation: the term shown at each registration for a meeting; the registration carries its version (decision 36)
@Service
public class ConsentService {

    public static final String TERMS = "terms";
    public static final String PRIVACY = "privacy";
    public static final String HEALTH = "health";
    public static final String PARTICIPATION = "participation";

    private final ConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final String termsVersion;
    private final String privacyVersion;
    private final String healthVersion;
    private final String participationVersion;

    public ConsentService(ConsentRepository consentRepository, UserRepository userRepository,
                          @Value("${consent.terms-version}") String termsVersion,
                          @Value("${consent.privacy-version}") String privacyVersion,
                          @Value("${consent.health-version}") String healthVersion,
                          @Value("${consent.participation-version}") String participationVersion) {
        if (termsVersion.isBlank() || privacyVersion.isBlank() || healthVersion.isBlank() || participationVersion.isBlank()) {
            throw new IllegalStateException("consent.terms-version, consent.privacy-version, consent.health-version "
                    + "and consent.participation-version must not be blank");
        }
        this.consentRepository = consentRepository;
        this.userRepository = userRepository;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.healthVersion = healthVersion;
        this.participationVersion = participationVersion;
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
        return !consentRepository.existsByUserIdAndDocumentAndVersionAndRevokedAtIsNull(userId, TERMS, termsVersion)
                || !consentRepository.existsByUserIdAndDocumentAndVersionAndRevokedAtIsNull(userId, PRIVACY, privacyVersion);
    }

    // ---- health (dependents only; the callers already know the user is a dependent) ----------------------------------

    @Transactional(readOnly = true)
    public boolean isHealthPending(Long userId) {
        return !consentRepository.existsByUserIdAndDocumentAndVersionAndRevokedAtIsNull(userId, HEALTH, healthVersion);
    }

    // The gate of every write of health data: no active row for the version in force, no write. It joins the caller's
    // transaction and reads the row FOR SHARE (not readOnly: Postgres refuses row locks in a read-only transaction), so a
    // revocation running at the same time cannot leave behind what this write stores (decision 35).
    @Transactional
    public void requireHealth(Long userId) {
        boolean onFile = !consentRepository
                .findByUserIdAndDocumentAndVersionAndRevokedAtIsNull(userId, HEALTH, healthVersion).isEmpty();
        if (!onFile) {
            throw new ConsentRequiredException("Consent to the processing of health data (version " + healthVersion
                    + ") must be accepted first");
        }
    }

    // POST /me/consents/health
    @Transactional
    public void acceptHealth(Long userId, String acceptedHealthVersion, String ip) {
        if (!healthVersion.equals(acceptedHealthVersion)) {
            throw new ConsentRequiredException("The health data consent (version " + healthVersion
                    + ") in force must be accepted");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));
        recordOne(user, HEALTH, healthVersion, ip);
    }

    // DELETE /me/consents/health: marks the acceptances in force as revoked (they stay as proof). It runs first in the
    // revocation's transaction, so it is the step that takes the row lock that requireHealth shares
    @Transactional
    public void revokeHealth(Long userId, Instant now) {
        consentRepository.revokeActive(userId, HEALTH, now);
    }

    // ---- participation term (carried by the registration for a meeting) ----------------------------------------------

    // only compares: a missing version counts as not accepted
    public void requireParticipation(String acceptedParticipationVersion) {
        if (!participationVersion.equals(acceptedParticipationVersion)) {
            throw new ConsentRequiredException("The participation term (version " + participationVersion
                    + ") in force must be accepted");
        }
    }

    // joins the registration's transaction: the acceptance commits or rolls back with the seat
    @Transactional
    public void recordParticipation(Long userId, String ip) {
        recordOne(userRepository.getReferenceById(userId), PARTICIPATION, participationVersion, ip);
    }

    // accepting a version that is already on file (and not revoked) adds nothing, so repeating the call is harmless; after
    // a revocation it adds a new row next to the revoked one (the unique index only covers active rows, V27)
    private void recordOne(User user, String document, String version, String ip) {
        if (consentRepository.existsByUserIdAndDocumentAndVersionAndRevokedAtIsNull(user.getId(), document, version)) {
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
