package plad.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.User;
import plad.entity.Sex;
import plad.entity.UserType;
import plad.repositories.UserRepository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class LoginControllerTest {

    private static final String PASSWORD = "senha-dev-123";
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    // the rate limiter is a singleton shared by the cached context, so every test logs in from its own address
    private static final AtomicInteger CLIENTS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginReturnsTokenThatAuthenticatesRequests() throws Exception {
        String response = login(nextClient(), newUser(), PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.token");

        mockMvc.perform(get("/triage/questions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void loginIgnoresEmailCaseAndSurroundingSpaces() throws Exception {
        login(nextClient(), "  " + newUser().toUpperCase(Locale.ROOT) + " ", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String client = nextClient();

        login(client, newUser(), "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
        login(client, "nobody-" + UUID.randomUUID() + "@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS));
    }

    @Test
    void loginWithoutFieldsIsBadRequest() throws Exception {
        perform(nextClient(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", allOf(containsString("email"), containsString("password"))));
    }

    @Test
    void passwordOverBcryptByteLimitIsBadRequest() throws Exception {
        // 40 chars but 80 UTF-8 bytes
        login(nextClient(), "nobody-" + UUID.randomUUID() + "@example.com", "ç".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("password must be at most 72 bytes")));
    }

    @Test
    void passwordOf72AsciiBytesIsAccepted() throws Exception {
        String password = "a".repeat(72);

        login(nextClient(), newUser(password), password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void sixthAttemptFromSameClientIsTooManyRequests() throws Exception {
        String client = nextClient();
        String email = newUser();
        for (int i = 0; i < 5; i++) {
            login(client, email, "wrong-password").andExpect(status().isUnauthorized());
        }

        // even the right password is refused until the bucket refills
        login(client, email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message", containsString("Too many requests")));
    }

    @Test
    void eleventhFailedAttemptOnSameEmailIsTooManyRequestsEvenFromDifferentClients() throws Exception {
        String email = newUser();
        for (int i = 0; i < 10; i++) {
            login(nextClient(), email, "wrong-password").andExpect(status().isUnauthorized());
        }

        // the budget is checked before the password, so the right one gets no answer either
        login(nextClient(), email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message", containsString("Too many requests")));
    }

    @Test
    void successfulLoginsDoNotCountTowardsEmailLimit() throws Exception {
        String email = newUser();

        // more than the 10-attempt budget, each from its own address
        for (int i = 0; i < 12; i++) {
            login(nextClient(), email, PASSWORD).andExpect(status().isOk());
        }
    }

    @Test
    void successAfterFailuresKeepsTheRemainingBudget() throws Exception {
        String email = newUser();
        for (int i = 0; i < 9; i++) {
            login(nextClient(), email, "wrong-password").andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 3; i++) {
            login(nextClient(), email, PASSWORD).andExpect(status().isOk());
        }

        // the 10th failure still gets its answer, the next attempt does not
        login(nextClient(), email, "wrong-password").andExpect(status().isUnauthorized());
        login(nextClient(), email, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void emailLimitIgnoresCaseAndSurroundingSpaces() throws Exception {
        String email = newUser();
        for (int i = 0; i < 10; i++) {
            String variant = i % 2 == 0 ? email.toUpperCase(Locale.ROOT) : "  " + email + " ";
            login(nextClient(), variant, "wrong-password").andExpect(status().isUnauthorized());
        }

        login(nextClient(), email, "wrong-password").andExpect(status().isTooManyRequests());
    }

    @Test
    void loginLimitDoesNotUseUpRegistrations() throws Exception {
        String client = nextClient();
        for (int i = 0; i < 5; i++) {
            perform(client, "{}").andExpect(status().isBadRequest());
        }
        perform(client, "{}").andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/auth/register")
                        .with(from(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // helpers

    private ResultActions login(String client, String email, String password) throws Exception {
        return perform(client, """
                {"email": "%s", "password": "%s"}""".formatted(email, password));
    }

    private ResultActions perform(String client, String body) throws Exception {
        return mockMvc.perform(post("/auth/login")
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
        return "10.2.0." + CLIENTS.incrementAndGet();
    }

    private String newUser() {
        return newUser(PASSWORD);
    }

    private String newUser(String password) {
        User user = new User();
        user.setRealName("login test");
        user.setEmail("login-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(UserType.dependente);
        return userRepository.save(user).getEmail();
    }
}
