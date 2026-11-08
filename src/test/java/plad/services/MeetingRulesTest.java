package plad.services;

import org.junit.jupiter.api.Test;
import plad.exception.InvalidMeetingException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The edges the MockMvc tests cannot hit exactly because they run on the real clock: a fixed Clock pins them (decisions 20 and 22)
class MeetingRulesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private static final Instant NOW = CLOCK.instant();

    @Test
    void startingExactly60MinutesFromNowIsAllowed() {
        Instant start = NOW.plus(60, ChronoUnit.MINUTES);

        assertThatCode(() -> MeetingRules.validate(start, start.plus(1, ChronoUnit.HOURS), 10, NOW, true))
                .doesNotThrowAnyException();
    }

    @Test
    void startingOneSecondShortOf60MinutesIsRejectedOnTheStartField() {
        Instant start = NOW.plus(60, ChronoUnit.MINUTES).minusSeconds(1);

        assertThatThrownBy(() -> MeetingRules.validate(start, start.plus(1, ChronoUnit.HOURS), 10, NOW, true))
                .isInstanceOfSatisfying(InvalidMeetingException.class, e ->
                        assertThat(e.getErrors()).extracting("field").containsExactly("starts_at"));
    }

    @Test
    void theLeadTimeIsSkippedWhenAnEditKeepsTheStart() {
        Instant start = NOW.plus(5, ChronoUnit.MINUTES);

        assertThatCode(() -> MeetingRules.validate(start, start.plus(1, ChronoUnit.HOURS), 10, NOW, false))
                .doesNotThrowAnyException();
    }

    @Test
    void durationEdgesAreInclusive() {
        Instant start = NOW.plus(2, ChronoUnit.HOURS);

        assertThatCode(() -> MeetingRules.validate(start, start.plus(15, ChronoUnit.MINUTES), 10, NOW, true)).doesNotThrowAnyException();
        assertThatCode(() -> MeetingRules.validate(start, start.plus(180, ChronoUnit.MINUTES), 10, NOW, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> MeetingRules.validate(start, start.plus(15, ChronoUnit.MINUTES).minusSeconds(1), 10, NOW, true))
                .isInstanceOf(InvalidMeetingException.class);
        assertThatThrownBy(() -> MeetingRules.validate(start, start.plus(180, ChronoUnit.MINUTES).plusSeconds(1), 10, NOW, true))
                .isInstanceOf(InvalidMeetingException.class);
    }

    @Test
    void capacityEdgesAreInclusive() {
        Instant start = NOW.plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        assertThatCode(() -> MeetingRules.validate(start, end, 2, NOW, true)).doesNotThrowAnyException();
        assertThatCode(() -> MeetingRules.validate(start, end, 30, NOW, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> MeetingRules.validate(start, end, 1, NOW, true)).isInstanceOf(InvalidMeetingException.class);
        assertThatThrownBy(() -> MeetingRules.validate(start, end, 31, NOW, true)).isInstanceOf(InvalidMeetingException.class);
    }

    @Test
    void theListWindowIsInclusiveAt62Days() {
        assertThatCode(() -> MeetingRules.validateWindow(NOW, NOW.plus(62, ChronoUnit.DAYS))).doesNotThrowAnyException();
        assertThatThrownBy(() -> MeetingRules.validateWindow(NOW, NOW.plus(62, ChronoUnit.DAYS).plusSeconds(1)))
                .isInstanceOf(InvalidMeetingException.class);
        assertThatThrownBy(() -> MeetingRules.validateWindow(NOW, NOW)).isInstanceOf(InvalidMeetingException.class);
    }

    // live room door: [starts_at - 10 min, ends_at)
    @Test
    void theJoinWindowOpensExactly10MinutesBeforeTheStartAndClosesExactlyAtTheEnd() {
        Instant start = NOW.plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        Instant opens = start.minus(10, ChronoUnit.MINUTES);

        assertThat(MeetingRules.joinWindowOpen(start, end, opens.minusSeconds(1))).isFalse();
        assertThat(MeetingRules.joinWindowOpen(start, end, opens)).isTrue();
        assertThat(MeetingRules.joinWindowOpen(start, end, start)).isTrue();
        assertThat(MeetingRules.joinWindowOpen(start, end, end.minusSeconds(1))).isTrue();
        assertThat(MeetingRules.joinWindowOpen(start, end, end)).isFalse();
    }
}
