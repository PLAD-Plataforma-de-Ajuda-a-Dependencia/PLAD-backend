package plad.controllers;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.UserRepository;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// PUT /me/password through the real filter chain. @Transactional: the rows each test writes roll back.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class PasswordChangeControllerTest {

    private static final String PASSWORD = "senha-dev-123";
    private static final String NEW_PASSWORD = "outra-senha-456";
    private static final String ORIGIN = "http://localhost:5173"; // the default cors.allowed-origins
    private static final String COOKIE = "refresh_token";

    // the rate limiter is a singleton shared by the cached context: every login comes from its own address
    private static final AtomicInteger CLIENTS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // --- the change

    @Test
    void changeWorksAndTheLoginAcceptsTheNewPasswordOnly() throws Exception {
        String email = newUser(UserType.dependente);
        String token = accessToken(login(email, PASSWORD).andExpect(status().isOk()).andReturn());

        MvcResult changed = change(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty()).andReturn();

        // a session like the login's: refresh token in the hardened cookie, never in the body
        String setCookie = changed.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Path=/auth").contains("Max-Age=" + 7 * 24 * 3600);
        assertThat(changed.getResponse().getContentAsString()).doesNotContain(cookieValue(changed));
        login(email, NEW_PASSWORD).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void theOldAccessTokenIsUnauthorizedAndTheNewOneWorks() throws Exception {
        String email = newUser(UserType.dependente);
        String oldToken = accessToken(login(email, PASSWORD).andReturn());
        String otherDevice = accessToken(login(email, PASSWORD).andReturn());
        probe(oldToken).andExpect(status().isOk());

        String newToken = accessToken(change(oldToken, PASSWORD, NEW_PASSWORD).andReturn());

        probe(newToken).andExpect(status().isOk());
        probe(oldToken).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        probe(otherDevice).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void theOldRefreshTokenIsUnauthorizedEvenOnTheOtherDevices() throws Exception {
        String email = newUser(UserType.dependente);
        MvcResult thisDevice = login(email, PASSWORD).andReturn();
        String otherDeviceRefresh = cookieValue(login(email, PASSWORD).andReturn());

        change(accessToken(thisDevice), PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        refresh(cookieValue(thisDevice)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(otherDeviceRefresh).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void theNewRefreshCookieKeepsThisSessionGoing() throws Exception {
        String email = newUser(UserType.dependente);
        String token = accessToken(login(email, PASSWORD).andReturn());
        MvcResult changed = change(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk()).andReturn();

        MvcResult refreshed = refresh(cookieValue(changed)).andExpect(status().isOk()).andReturn();

        probe(accessToken(refreshed)).andExpect(status().isOk());
    }

    @Test
    void otherUsersKeepTheirSessions() throws Exception {
        String email = newUser(UserType.dependente);
        String token = accessToken(login(email, PASSWORD).andReturn());
        MvcResult bystander = login(newUser(UserType.dependente), PASSWORD).andReturn();

        change(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        probe(accessToken(bystander)).andExpect(status().isOk());
        refresh(cookieValue(bystander)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void everyRoleCanChangeItsPassword(UserType type) throws Exception {
        String email = newUser(type);
        String token = accessToken(login(email, PASSWORD).andReturn());

        change(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(put("/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content(body(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // --- the current password

    @Test
    void wrongCurrentPasswordIsUnauthorizedAndChangesNothing() throws Exception {
        String email = newUser(UserType.dependente);
        MvcResult session = login(email, PASSWORD).andReturn();
        String token = accessToken(session);

        change(token, "not-the-password", NEW_PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        probe(token).andExpect(status().isOk());
        refresh(cookieValue(session)).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isOk());
        login(email, NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void theEleventhFailureInARowIsTooManyRequests() throws Exception {
        String email = newUser(UserType.dependente);
        String token = accessToken(login(email, PASSWORD).andReturn());

        for (int i = 0; i < 10; i++) {
            change(token, "wrong-" + i, NEW_PASSWORD).andExpect(status().isUnauthorized());
        }

        change(token, "wrong-11", NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // an exhausted budget answers 429 whether or not the password is right, so nothing leaks
        change(token, PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests());
        login(email, NEW_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void failuresOnTheLoginAndOnThePasswordChangeShareOneBudget() throws Exception {
        String email = newUser(UserType.dependente);
        String token = accessToken(login(email, PASSWORD).andReturn());
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-" + i).andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 5; i++) {
            change(token, "wrong-" + i, NEW_PASSWORD).andExpect(status().isUnauthorized());
        }

        change(token, "wrong-last", NEW_PASSWORD).andExpect(status().isTooManyRequests());
        login(email, "wrong-last").andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulChangesGiveTheirSlotBack() throws Exception {
        String email = newUser(UserType.dependente);
        String token = accessToken(login(email, PASSWORD).andReturn());
        String current = PASSWORD;

        // more than the 10 slots of the budget: every success must have handed its slot back
        for (int i = 0; i < 12; i++) {
            String next = "senha-nova-" + i + "-abc";
            token = accessToken(change(token, current, next).andExpect(status().isOk()).andReturn());
            current = next;
        }

        change(token, "wrong", NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    // --- the new password

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "   ", "curta", "1234567"})
    void aWeakNewPasswordIsBadRequestAndKeepsTheOldOne(String weak) throws Exception {
        String email = newUser(UserType.dependente);
        String token = accessToken(login(email, PASSWORD).andReturn());

        change(token, PASSWORD, weak).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("new_password"));

        probe(token).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aNewPasswordOver72BytesIsBadRequest() throws Exception {
        String email = newUser(UserType.dependente);
        String token = accessToken(login(email, PASSWORD).andReturn());

        change(token, PASSWORD, "a".repeat(73)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // 37 two-byte characters: under 72 characters but 74 bytes
        change(token, PASSWORD, "é".repeat(37)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // exactly 72 bytes is fine
        change(token, PASSWORD, "a".repeat(72)).andExpect(status().isOk());
    }

    @Test
    void aMissingFieldIsBadRequest() throws Exception {
        String token = accessToken(login(newUser(UserType.dependente), PASSWORD).andReturn());

        mockMvc.perform(put("/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"current_password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void theNewPasswordEqualToTheCurrentOneIsBadRequestAndEndsNoSession() throws Exception {
        String email = newUser(UserType.dependente);
        MvcResult session = login(email, PASSWORD).andReturn();
        String token = accessToken(session);

        change(token, PASSWORD, PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_CHANGE"));

        probe(token).andExpect(status().isOk());
        refresh(cookieValue(session)).andExpect(status().isOk());
    }

    @Test
    void theNewPasswordEqualToTheCurrentOneIsNeverSaidToSomeoneWhoDoesNotKnowIt() throws Exception {
        String token = accessToken(login(newUser(UserType.dependente), PASSWORD).andReturn());

        // the same text in both fields, but it is not the current password: a plain wrong password
        change(token, "not-the-password", "not-the-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // --- helpers

    private ResultActions change(String token, String current, String next) throws Exception {
        return mockMvc.perform(put("/me/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(current, next)));
    }

    private static String body(String current, String next) {
        return """
                {"current_password": "%s", "new_password": "%s"}""".formatted(current, next);
    }

    // any authenticated route that needs no particular role
    private ResultActions probe(String token) throws Exception {
        return mockMvc.perform(get("/triage/questions").header("Authorization", "Bearer " + token));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").with(from(nextClient()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password)));
    }

    private ResultActions refresh(String rawToken) throws Exception {
        return mockMvc.perform(post("/auth/refresh").with(from(nextClient())).header(HttpHeaders.ORIGIN, ORIGIN)
                .cookie(new Cookie(COOKIE, rawToken)));
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    // value of the refresh_token cookie set by the response
    private static String cookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).as("Set-Cookie").isNotNull();
        return setCookie.substring((COOKIE + "=").length(), setCookie.indexOf(';'));
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String nextClient() {
        return "10.4.0." + CLIENTS.incrementAndGet();
    }

    private String newUser(UserType type) {
        User user = new User();
        user.setRealName("password test");
        user.setEmail("password-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        return userRepository.save(user).getEmail();
    }
}
