package plad.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaxBytesValidatorTest {

    private record Sample(@MaxBytes(4) String value) {}

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void nullIsValid() {
        assertTrue(isValid(null));
    }

    @Test
    void asciiCountsOneBytePerChar() {
        assertTrue(isValid("abcd"));
        assertFalse(isValid("abcde"));
    }

    @Test
    void multiByteCharsCountAllTheirBytes() {
        assertTrue(isValid("çç")); // 2 chars, 4 bytes
        assertFalse(isValid("ççç")); // 3 chars, 6 bytes
        assertTrue(isValid("😀")); // 1 code point, 4 bytes
        assertFalse(isValid("😀a"));
    }

    @Test
    void defaultMessageStatesTheLimit() {
        String message = validator.validate(new Sample("abcde")).iterator().next().getMessage();

        assertEquals("password must be at most 4 bytes", message);
    }

    private boolean isValid(String value) {
        return validator.validate(new Sample(value)).isEmpty();
    }
}
