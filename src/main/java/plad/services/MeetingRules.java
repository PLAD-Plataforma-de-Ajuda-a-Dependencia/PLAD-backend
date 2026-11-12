package plad.services;

import plad.dto.FieldErrorDTO;
import plad.exception.InvalidMeetingException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

// The limits the team decided (decisions 13, 20 and 22), in one place. The meeting limits are CHECKs in V25 too.
// Every broken rule is reported, so the form can mark all the fields at once.
final class MeetingRules {

    static final Duration MIN_DURATION = Duration.ofMinutes(15);
    static final Duration MAX_DURATION = Duration.ofMinutes(180);
    static final Duration MIN_LEAD_TIME = Duration.ofMinutes(60);
    static final int MIN_CAPACITY = 2;
    static final int MAX_CAPACITY = 30;
    static final Duration MAX_LIST_WINDOW = Duration.ofDays(62);
    // live room: people can come in this long before the start, and until the end
    static final Duration JOIN_LEAD_TIME = Duration.ofMinutes(10);
    // How long a join token is valid. LiveKit only checks it on the first connection (it renews the token of whoever
    // is connected), and it does not invalidate the token of someone removed from the room, so this short life is what
    // bounds a removed person (or anyone, after an early end) coming back straight to LiveKit. The front asks
    // POST /join for a new token on every entry and never keeps one.
    static final Duration TOKEN_TTL = Duration.ofMinutes(2);
    // How long a psychologist keeps reading the triage of a dependent registered in a meeting of theirs (decision 13):
    // until the end of the 30th calendar day after the day of the meeting's ends_at, counted in Sao Paulo. A meeting that
    // ends on Nov 1 (any time) can be read through Dec 1 and not on Dec 2. Scheduled meetings follow the same rule, so a
    // meeting nobody ended does not give access forever. TriageRepository#findVisibleToPsychologist* apply it in the query.
    static final int TRIAGE_ACCESS_DAYS_AFTER_END = 30;
    static final ZoneId CALENDAR_ZONE = ZoneId.of("America/Sao_Paulo");

    private MeetingRules() {
    }

    // The earliest ends_at a meeting may have and still give access now: the start (Sao Paulo) of the day that is
    // TRIAGE_ACCESS_DAYS_AFTER_END days before today. A meeting gives access while ends_at >= this instant.
    static Instant triageAccessCutoff(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, CALENDAR_ZONE);
        return today.minusDays(TRIAGE_ACCESS_DAYS_AFTER_END).atStartOfDay(CALENDAR_ZONE).toInstant();
    }

    // [starts_at - 10 min, ends_at)
    static boolean joinWindowOpen(Instant startsAt, Instant endsAt, Instant now) {
        return !now.isBefore(startsAt.minus(JOIN_LEAD_TIME)) && now.isBefore(endsAt);
    }

    // checkLeadTime: true when creating, and when an edit moves the start (an untouched start may already be close)
    static void validate(Instant startsAt, Instant endsAt, int capacity, Instant now, boolean checkLeadTime) {
        List<FieldErrorDTO> errors = new ArrayList<>();
        if (checkLeadTime && startsAt.isBefore(now.plus(MIN_LEAD_TIME))) {
            errors.add(new FieldErrorDTO("starts_at", "must be at least 60 minutes from now"));
        }
        if (!endsAt.isAfter(startsAt)) {
            errors.add(new FieldErrorDTO("ends_at", "must be after starts_at"));
        } else {
            Duration duration = Duration.between(startsAt, endsAt);
            if (duration.compareTo(MIN_DURATION) < 0) {
                errors.add(new FieldErrorDTO("ends_at", "the meeting must last at least 15 minutes"));
            } else if (duration.compareTo(MAX_DURATION) > 0) {
                errors.add(new FieldErrorDTO("ends_at", "the meeting must last at most 180 minutes"));
            }
        }
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            errors.add(new FieldErrorDTO("capacity", "must be between 2 and 30"));
        }
        if (!errors.isEmpty()) {
            throw new InvalidMeetingException(errors);
        }
    }

    static void validateWindow(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new InvalidMeetingException("to", "must be after from");
        }
        if (Duration.between(from, to).compareTo(MAX_LIST_WINDOW) > 0) {
            throw new InvalidMeetingException("to", "the window must be at most 62 days");
        }
    }
}
