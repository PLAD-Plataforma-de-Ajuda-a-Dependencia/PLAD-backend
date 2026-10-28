package plad.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AuthControllerTest {

    // the rate limiter is a singleton shared by the cached context, so every test registers from its own address
    private static final AtomicInteger CLIENTS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerDependentStoresLowercasedEmail() throws Exception {
        String email = "Ana-" + UUID.randomUUID() + "@Example.COM";

        register(nextClient(), dependentBody(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.real_name").value("Ana Teste"))
                .andExpect(jsonPath("$.email").value(email.toLowerCase(Locale.ROOT)));
    }

    @Test
    void registerPsychologistIsCreated() throws Exception {
        register(nextClient(), psychologistBody(newEmail(), newCredentialNumber()))
                .andExpect(status().isCreated());
    }

    @Test
    void sameEmailWithDifferentCaseIsConflict() throws Exception {
        String client = nextClient();
        String email = newEmail();
        register(client, dependentBody(email)).andExpect(status().isCreated());

        register(client, dependentBody(email.toUpperCase(Locale.ROOT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"))
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    void psychologistWithoutCredentialsIsBadRequest() throws Exception {
        register(nextClient(), dependentBody(newEmail()).replace("dependente", "psicologo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION"))
                .andExpect(jsonPath("$.message").value("Psychologist must provide credentials"));
    }

    @Test
    void psychologistWithBlankCredentialNumberIsBadRequest() throws Exception {
        register(nextClient(), psychologistBody(newEmail(), "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION"))
                .andExpect(jsonPath("$.message").value("Psychologist must provide credentials"));
    }

    @Test
    void nameLongerThanTheColumnIsBadRequest() throws Exception {
        register(nextClient(), dependentBody(newEmail()).replace("Ana Teste", "a".repeat(151)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("real_name"))
                .andExpect(jsonPath("$.message", containsString("real_name")));
    }

    @Test
    void passwordOverBcryptByteLimitIsBadRequest() throws Exception {
        // 40 chars but 80 UTF-8 bytes: used to pass @Size(max = 72) and blow up in BCrypt
        register(nextClient(), dependentBody(newEmail()).replace("senha-dev-123", "ç".repeat(40)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.message", containsString("password must be at most 72 bytes")));
    }

    @Test
    void passwordOf72AsciiBytesIsAccepted() throws Exception {
        register(nextClient(), dependentBody(newEmail()).replace("senha-dev-123", "a".repeat(72)))
                .andExpect(status().isCreated());
    }

    @Test
    void futureBirthDateIsBadRequest() throws Exception {
        register(nextClient(), dependentBody(newEmail()).replace("2000-05-20", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("birth_date"))
                .andExpect(jsonPath("$.message", containsString("birth_date")));
    }

    @Test
    void unknownEnumValueIsBadRequest() throws Exception {
        register(nextClient(), dependentBody(newEmail()).replace("nao_binario", "not_a_gender"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        register(nextClient(), "{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void sixthRegistrationFromSameClientIsTooManyRequests() throws Exception {
        String client = nextClient();
        for (int i = 0; i < 5; i++) {
            register(client, "{}").andExpect(status().isBadRequest());
        }

        register(client, "{}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    // helpers

    private ResultActions register(String client, String body) throws Exception {
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

    private static String nextClient() {
        return "10.1.0." + CLIENTS.incrementAndGet();
    }

    private static String newEmail() {
        return "register-" + UUID.randomUUID() + "@example.com";
    }

    private static String newCredentialNumber() {
        return UUID.randomUUID().toString().substring(0, 20);
    }

    private static String dependentBody(String email) {
        return """
                {"real_name": "Ana Teste", "email": "%s", "password": "senha-dev-123",
                 "birth_date": "2000-05-20", "gender": "nao_binario", "type_user": "dependente"}""".formatted(email);
    }

    private static String psychologistBody(String email, String crmCrpNumber) {
        return """
                {"real_name": "Psi Teste", "email": "%s", "password": "senha-dev-123",
                 "birth_date": "1985-03-10", "gender": "feminino", "type_user": "psicologo",
                 "credential_type": "CRP", "crm_crp_number": "%s", "graduate": "Psicologia"}""".formatted(email, crmCrpNumber);
    }
}
