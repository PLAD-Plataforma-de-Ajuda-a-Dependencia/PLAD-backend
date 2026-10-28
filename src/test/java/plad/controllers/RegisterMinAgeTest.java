package plad.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// the platform is only for adults; "today" comes from the Clock bean, replaced here so every date is deterministic
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class RegisterMinAgeTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final AtomicInteger CLIENTS = new AtomicInteger();
    private static final String TOO_YOUNG = "must be at least 18 years old";

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {

        private volatile Instant instant = Instant.parse("2026-10-28T15:00:00Z");

        void setNow(String offsetDateTime) {
            this.instant = OffsetDateTime.parse(offsetDateTime).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return SAO_PAULO;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MutableClock clock;

    @Test
    void turning18TodayIsCreated() throws Exception {
        clock.setNow("2026-10-28T12:00:00-03:00");

        register(dependentBody("2008-10-28")).andExpect(status().isCreated());
    }

    @Test
    void turning18TomorrowIsBadRequest() throws Exception {
        clock.setNow("2026-10-28T12:00:00-03:00");

        register(dependentBody("2008-10-29")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("birth_date"))
                .andExpect(jsonPath("$.errors[0].message").value(TOO_YOUNG));
    }

    @Test
    void bornOnLeapDayIsAnAdultOnlyFromMarchFirstInANonLeapYear() throws Exception {
        clock.setNow("2026-02-28T12:00:00-03:00");
        register(dependentBody("2008-02-29")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("birth_date"))
                .andExpect(jsonPath("$.errors[0].message").value(TOO_YOUNG));

        clock.setNow("2026-03-01T12:00:00-03:00");
        register(dependentBody("2008-02-29")).andExpect(status().isCreated());
    }

    @Test
    void eveOfTheBirthdayAt2330InSaoPauloIsBadRequestEvenThoughItIsTheBirthdayInUtc() throws Exception {
        // 2026-05-20T23:30-03:00 = 2026-05-21T02:30Z: in UTC the person would already be 18
        clock.setNow("2026-05-20T23:30:00-03:00");

        register(dependentBody("2008-05-21")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("birth_date"))
                .andExpect(jsonPath("$.errors[0].message").value(TOO_YOUNG));
    }

    @Test
    void futureBirthDateIsBadRequest() throws Exception {
        clock.setNow("2026-10-28T12:00:00-03:00");

        register(dependentBody("2026-10-29")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("birth_date"))
                .andExpect(jsonPath("$.errors[0].message").value(TOO_YOUNG));
    }

    @Test
    void ageOver120IsBadRequest() throws Exception {
        clock.setNow("2026-10-28T12:00:00-03:00");

        register(dependentBody("1905-10-28")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("birth_date"));
    }

    @Test
    void psychologistUnder18IsBadRequestToo() throws Exception {
        clock.setNow("2026-10-28T12:00:00-03:00");

        register(psychologistBody("2008-10-29")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("birth_date"))
                .andExpect(jsonPath("$.errors[0].message").value(TOO_YOUNG));
        register(psychologistBody("2008-10-28")).andExpect(status().isCreated());
    }

    // helpers

    private ResultActions register(String body) throws Exception {
        String client = "10.2.0." + CLIENTS.incrementAndGet();
        return mockMvc.perform(post("/auth/register")
                .with(from(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String dependentBody(String birthDate) {
        return """
                {"real_name": "Ana Teste", "email": "%s", "password": "senha-dev-123",
                 "birth_date": "%s", "gender": "nao_binario", "type_user": "dependente"}"""
                .formatted(newEmail(), birthDate);
    }

    private static String psychologistBody(String birthDate) {
        return """
                {"real_name": "Psi Teste", "email": "%s", "password": "senha-dev-123",
                 "birth_date": "%s", "gender": "feminino", "type_user": "psicologo",
                 "credential_type": "CRP", "crm_crp_number": "%s", "graduate": "Psicologia"}"""
                .formatted(newEmail(), birthDate, UUID.randomUUID().toString().substring(0, 20));
    }

    private static String newEmail() {
        return "min-age-" + UUID.randomUUID() + "@example.com";
    }
}
