package plad.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrmCrpNumbersTest {

    @Test
    void removesSeparatorsAndSpaces() {
        assertThat(CrmCrpNumbers.normalize("06/12345")).isEqualTo("0612345");
        assertThat(CrmCrpNumbers.normalize("06 / 12345")).isEqualTo("0612345");
        assertThat(CrmCrpNumbers.normalize(" 06-12345\t")).isEqualTo("0612345");
    }

    @Test
    void uppercasesLetters() {
        assertThat(CrmCrpNumbers.normalize("123456/sp")).isEqualTo("123456SP");
    }

    @Test
    void dropsNonAsciiCharacters() {
        assertThat(CrmCrpNumbers.normalize("06/123ç45")).isEqualTo("0612345");
    }

    @Test
    void isEmptyWhenNothingAlphanumericRemains() {
        assertThat(CrmCrpNumbers.normalize(" // - ")).isEmpty();
    }
}
