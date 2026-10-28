package plad.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Clock;
import java.time.LocalDate;

// Spring creates this validator and injects the Clock bean
public class MinAgeValidator implements ConstraintValidator<MinAge, LocalDate> {

    private static final int MAX_AGE = 120;

    private final Clock clock;
    private int min;

    public MinAgeValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void initialize(MinAge constraint) {
        this.min = constraint.value();
    }

    // null is valid: presence is @NotNull's job
    @Override
    public boolean isValid(LocalDate birthDate, ConstraintValidatorContext context) {
        if (birthDate == null) {
            return true;
        }
        LocalDate today = LocalDate.now(clock);

        // a future date turns `min` even later, so it fails here too
        if (birthday(birthDate, min).isAfter(today)) {
            return false;
        }
        // over MAX_AGE: the person has already turned MAX_AGE + 1
        if (!birthday(birthDate, MAX_AGE + 1).isAfter(today)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "must be a plausible birth date (at most " + MAX_AGE + " years old)").addConstraintViolation();
            return false;
        }
        return true;
    }

    // the date a person born on `birthDate` turns `years`: same day and month. Born on Feb 29, that is Mar 1 in a
    // non-leap year (Código Civil, art. 132, § 3º), while LocalDate.plusYears would give Feb 28
    private static LocalDate birthday(LocalDate birthDate, int years) {
        LocalDate birthday = birthDate.plusYears(years);
        boolean bornOnLeapDay = birthDate.getMonthValue() == 2 && birthDate.getDayOfMonth() == 29;
        return bornOnLeapDay && !birthday.isLeapYear() ? birthday.plusDays(1) : birthday;
    }
}
