package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.repositories.MeetingRegistrationRepository;
import plad.repositories.TriageRepository;

import java.time.Clock;
import java.time.Instant;

// Withdrawing the health data consent (LGPD art. 8, § 5 and art. 18, IX; decision 35), in one transaction:
// 1. the acceptances in force get revoked_at (the rows stay as proof). First on purpose: it takes the row lock that
//    ConsentService#requireHealth shares, so a triage or a registration being written right now finishes before the
//    steps below run, and is erased by them;
// 2. the triage and its answers are deleted (psychologists stop reading it, GET /me says has_triage=false);
// 3. the registrations for meetings that have not started are cancelled; the ones for meetings already started stay.
// Afterwards GET /me says health_consent_pending=true and the triage and the registration ask for the consent again.
// Idempotent: with nothing in force every step changes nothing. No AuditAction, like the other acceptances: the consents
// table itself is the record. The route's @PreAuthorize lets only dependents in.
@Service
public class HealthConsentRevocationService {

    private final ConsentService consentService;
    private final TriageRepository triageRepository;
    private final MeetingRegistrationRepository registrationRepository;
    private final Clock clock;

    public HealthConsentRevocationService(ConsentService consentService, TriageRepository triageRepository,
                                          MeetingRegistrationRepository registrationRepository, Clock clock) {
        this.consentService = consentService;
        this.triageRepository = triageRepository;
        this.registrationRepository = registrationRepository;
        this.clock = clock;
    }

    @Transactional
    public void revoke(Long dependentId) {
        Instant now = clock.instant();
        consentService.revokeHealth(dependentId, now);
        triageRepository.deleteByDependentId(dependentId);
        registrationRepository.deleteNotStartedByDependent(dependentId, now);
    }
}
