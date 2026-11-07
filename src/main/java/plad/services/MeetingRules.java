package plad.services;

import plad.dto.FieldErrorDTO;
import plad.exception.InvalidMeetingException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// The limits the team decided (decisions 20 and 22), in one place. The same numbers are CHECKs in V25.
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
    // a join token outlives the meeting by this much (a late reconnection, an overrun)
    static final Duration TOKEN_GRACE = Duration.ofMinutes(15);

    private MeetingRules() {
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
