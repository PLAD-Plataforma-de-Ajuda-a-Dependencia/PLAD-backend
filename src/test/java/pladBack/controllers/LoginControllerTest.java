package pladBack.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import pladBack.entity.User;
import pladBack.entity.sex;
import pladBack.entity.userType;
import pladBack.repositories.userRepository;

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
@Transactional
class LoginControllerTest {

    private static final String PASSWORD = "senha-dev-123";
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    // the rate limiter is a singleton shared by the cached context, so every test logs in from its own address
    private static final AtomicInteger CLIENTS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private userRepository userRepository;
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
    void sixthAttemptOnSameEmailIsTooManyRequestsEvenFromDifferentClients() throws Exception {
        String email = newUser();
        for (int i = 0; i < 5; i++) {
            login(nextClient(), email, "wrong-password").andExpect(status().isUnauthorized());
        }

        login(nextClient(), email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void emailLimitIgnoresCaseAndSurroundingSpaces() throws Exception {
        String email = newUser();
        for (int i = 0; i < 5; i++) {
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
        User user = new User();
        user.setReal_name("login test");
        user.setEmail("login-" + UUID.randomUUID() + "@example.com");
        user.setPassword_hash(passwordEncoder.encode(PASSWORD));
        user.setBirth_date(LocalDate.of(2000, 1, 1));
        user.setGender(sex.outro);
        user.setTypeUser(userType.dependente);
        return userRepository.save(user).getEmail();
    }
}
