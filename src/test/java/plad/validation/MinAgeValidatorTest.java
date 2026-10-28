package plad.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinAgeValidatorTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private record Sample(@MinAge(value = 18) LocalDate birthDate) {}

    @Test
    void nullIsValid() {
        assertTrue(violations(at("2026-10-28T12:00:00-03:00"), null).isEmpty());
    }

    @Test
    void turning18TodayIsValid() {
        assertTrue(isValid("2026-10-28T12:00:00-03:00", "2008-10-28"));
    }

    @Test
    void turning18TomorrowIsInvalid() {
        assertFalse(isValid("2026-10-28T12:00:00-03:00", "2008-10-29"));
    }

    @Test
    void anAdultIsValid() {
        assertTrue(isValid("2026-10-28T12:00:00-03:00", "1990-05-20"));
    }

    @Test
    void bornOnLeapDayTurns18OnMarchFirstInANonLeapYear() {
        assertFalse(isValid("2026-02-28T12:00:00-03:00", "2008-02-29"));
        assertTrue(isValid("2026-03-01T12:00:00-03:00", "2008-02-29"));
    }

    @Test
    void bornOnLeapDayInOtherYearsAlsoTurns18OnMarchFirst() {
        // 18 years after a leap year is never a leap year, so it is always March 1
        assertFalse(isValid("2022-02-28T12:00:00-03:00", "2004-02-29"));
        assertTrue(isValid("2022-03-01T12:00:00-03:00", "2004-02-29"));
        assertFalse(isValid("2030-02-28T12:00:00-03:00", "2012-02-29"));
        assertTrue(isValid("2030-03-01T12:00:00-03:00", "2012-02-29"));
    }

    @Test
    void eveOfTheBirthdayAt2330InSaoPauloIsStillInvalidEvenThoughItIsAlreadyTheBirthdayInUtc() {
        // 2026-05-20T23:30-03:00 is 2026-05-21T02:30Z
        assertFalse(isValid("2026-05-20T23:30:00-03:00", "2008-05-21"));
        assertTrue(isValid("2026-05-21T00:00:00-03:00", "2008-05-21"));
    }

    @Test
    void futureDateIsInvalid() {
        assertFalse(isValid("2026-10-28T12:00:00-03:00", "2026-10-29"));
        assertFalse(isValid("2026-10-28T12:00:00-03:00", "2030-01-01"));
    }

    @Test
    void under18MessageStatesTheMinimum() {
        Set<ConstraintViolation<Sample>> violations = violations(at("2026-10-28T12:00:00-03:00"), LocalDate.parse("2008-10-29"));

        assertEquals("must be at least 18 years old", violations.iterator().next().getMessage());
    }

    @Test
    void age120IsValidAndAge121IsNot() {
        // born 1906-10-28: 120 today, turns 121 on 2027-10-28
        assertTrue(isValid("2026-10-28T12:00:00-03:00", "1906-10-28"));
        assertTrue(isValid("2027-10-27T12:00:00-03:00", "1906-10-28"));
        assertFalse(isValid("2027-10-28T12:00:00-03:00", "1906-10-28"));
        assertFalse(isValid("2026-10-28T12:00:00-03:00", "1905-10-28"));
    }

    @Test
    void over120MessageIsNotTheMinimumOne() {
        Set<ConstraintViolation<Sample>> violations = violations(at("2026-10-28T12:00:00-03:00"), LocalDate.parse("1900-01-01"));

        assertEquals("must be a plausible birth date (at most 120 years old)", violations.iterator().next().getMessage());
    }

    private static boolean isValid(String now, String birthDate) {
        return violations(at(now), LocalDate.parse(birthDate)).isEmpty();
    }

    private static Clock at(String offsetDateTime) {
        return Clock.fixed(OffsetDateTime.parse(offsetDateTime).toInstant(), SAO_PAULO);
    }

    // the validator takes the Clock by constructor, so the default factory (no-arg only) cannot build it
    private static Set<ConstraintViolation<Sample>> violations(Clock clock, LocalDate birthDate) {
        ConstraintValidatorFactory factory = new ConstraintValidatorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                try {
                    return key == MinAgeValidator.class
                            ? (T) new MinAgeValidator(clock)
                            : key.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public void releaseInstance(ConstraintValidator<?, ?> instance) {
            }
        };
        try (var validatorFactory = Validation.byDefaultProvider().configure()
                .constraintValidatorFactory(factory).buildValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            return validator.validate(new Sample(birthDate));
        }
    }
}
