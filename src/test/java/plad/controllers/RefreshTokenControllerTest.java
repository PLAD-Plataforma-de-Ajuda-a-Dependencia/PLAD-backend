package plad.controllers;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
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
import plad.entity.RefreshToken;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.RefreshTokenRepository;
import plad.repositories.UserRepository;
import plad.services.RefreshTokenService;
import plad.services.UserSessionService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// login -> refresh -> logout through the real filter chain. @Transactional: the rows each test writes roll back,
// and the refresh service joins the test transaction, so a reuse revocation is visible to the next request.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class RefreshTokenControllerTest {

    private static final String PASSWORD = "senha-dev-123";
    private static final String ORIGIN = "http://localhost:5173"; // the default cors.allowed-origins
    private static final String COOKIE = "refresh_token";

    // the rate limiter is a singleton shared by the cached context: every test comes from its own address
    private static final AtomicInteger CLIENTS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserSessionService userSessionService;

    // --- login

    @Test
    void loginSetsTheRefreshCookieWithHardenedAttributes() throws Exception {
        MvcResult result = login(newUser()).andExpect(status().isOk()).andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(COOKIE + "=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/auth")
                .contains("Max-Age=" + 7 * 24 * 3600);
        // the refresh token never appears in the body
        assertThat(result.getResponse().getContentAsString()).doesNotContain(cookieValue(result));
    }

    @Test
    void refreshTokenIsStoredOnlyAsItsSha256Hash() throws Exception {
        String email = newUser();
        String raw = cookieValue(login(email).andReturn());

        List<RefreshToken> stored = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userRepository.findByEmail(email).orElseThrow().getId()))
                .toList();

        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).hasSize(64)
                .isNotEqualTo(raw);
        assertThat(stored.getFirst().getRevokedAt()).isNull();
        assertThat(stored.getFirst().getExpiresAt()).isBetween(
                Instant.now().plusSeconds(7 * 24 * 3600 - 60), Instant.now().plusSeconds(7 * 24 * 3600 + 60));
    }

    @Test
    void accessTokenFromLoginIsShortLived() throws Exception {
        String body = login(newUser()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        long lifetimeSeconds = tokenLifetimeSeconds(token);
        assertThat(lifetimeSeconds).isEqualTo(15 * 60);
    }

    // --- rotation

    @Test
    void refreshRotatesTheTokenAndIssuesAWorkingPair() throws Exception {
        String email = newUser();
        MvcResult loggedIn = login(email).andReturn();
        String first = cookieValue(loggedIn);

        MvcResult refreshed = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        String second = cookieValue(refreshed);

        assertThat(second).isNotEqualTo(first);
        String setCookie = refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict").contains("Path=/auth");
        String newAccess = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.token");
        mockMvc.perform(get("/triage/questions").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
        // the first token was revoked, the second is the only active one
        assertThat(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(first)).orElseThrow().getRevokedAt())
                .isNotNull();
        assertThat(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(second)).orElseThrow().getRevokedAt())
                .isNull();
        // and the new one can be rotated again
        refresh(second).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieIsUnauthorizedAndClearsTheCookie() throws Exception {
        MvcResult result = refresh(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=;").contains("Max-Age=0");
    }

    @Test
    void refreshWithUnknownTokenIsUnauthorized() throws Exception {
        refresh("never-issued-" + UUID.randomUUID())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshWithExpiredTokenIsUnauthorizedAndRevokesNothingElse() throws Exception {
        String email = newUser();
        String live = cookieValue(login(email).andReturn());
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        String expired = "expired-" + UUID.randomUUID();
        refreshTokenRepository.save(new RefreshToken(userId, RefreshTokenService.hash(expired),
                Instant.now().minusSeconds(60)));

        refresh(expired).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        assertThat(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(live)).orElseThrow().getRevokedAt())
                .isNull();
    }

    // --- reuse

    @Test
    void reusingARotatedTokenRevokesEverySessionOfThatUser() throws Exception {
        String email = newUser();
        String first = cookieValue(login(email).andReturn());
        String otherDevice = cookieValue(login(email).andReturn());
        MvcResult rotated = refresh(first).andExpect(status().isOk()).andReturn();
        String second = cookieValue(rotated);

        // the stolen copy of the first token is replayed
        refresh(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // the legitimate new token and the other device's token are dead too
        refresh(second).andExpect(status().isUnauthorized());
        refresh(otherDevice).andExpect(status().isUnauthorized());
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        assertThat(refreshTokenRepository.findAll()).filteredOn(t -> t.getUserId().equals(userId))
                .allSatisfy(t -> assertThat(t.getRevokedAt()).isNotNull());
    }

    @Test
    void reuseDoesNotTouchTheSessionsOfOtherUsers() throws Exception {
        String victim = cookieValue(login(newUser()).andReturn());
        String bystander = cookieValue(login(newUser()).andReturn());
        refresh(victim).andExpect(status().isOk());

        refresh(victim).andExpect(status().isUnauthorized());

        refresh(bystander).andExpect(status().isOk());
    }

    // --- logout

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        String raw = cookieValue(login(newUser()).andReturn());

        MvcResult result = logout(raw).andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(COOKIE + "=;").contains("Max-Age=0").contains("Path=/auth")
                .contains("HttpOnly").contains("SameSite=Strict");
        assertThat(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(raw)).orElseThrow().getRevokedAt())
                .isNotNull();
        refresh(raw).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCookieIsStillNoContent() throws Exception {
        logout(null).andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void logoutOnlyEndsTheSessionOfTheCookieItReceived() throws Exception {
        String email = newUser();
        String phone = cookieValue(login(email).andReturn());
        String laptop = cookieValue(login(email).andReturn());

        logout(phone).andExpect(status().isNoContent());

        refresh(laptop).andExpect(status().isOk());
    }

    // --- token_version

    @Test
    void accessTokenWithAnOldTokenVersionIsUnauthorized() throws Exception {
        String email = newUser();
        String body = login(email).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        mockMvc.perform(get("/triage/questions").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        userSessionService.revokeAllSessions(userRepository.findByEmail(email).orElseThrow().getId());

        mockMvc.perform(get("/triage/questions").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void revokingAllSessionsAlsoKillsTheRefreshTokensButANewLoginWorks() throws Exception {
        String email = newUser();
        String raw = cookieValue(login(email).andReturn());

        userSessionService.revokeAllSessions(userRepository.findByEmail(email).orElseThrow().getId());

        refresh(raw).andExpect(status().isUnauthorized());
        String body = login(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String fresh = JsonPath.read(body, "$.token");
        mockMvc.perform(get("/triage/questions").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    // --- Origin

    @Test
    void refreshFromAnOriginOutsideTheAllowListIsForbidden() throws Exception {
        String raw = cookieValue(login(newUser()).andReturn());

        mockMvc.perform(post("/auth/refresh").with(from(nextClient())).cookie(new Cookie(COOKIE, raw))
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // refused before any rotation: the token is still good
        refresh(raw).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutAnOriginHeaderIsForbidden() throws Exception {
        String raw = cookieValue(login(newUser()).andReturn());

        mockMvc.perform(post("/auth/refresh").with(from(nextClient())).cookie(new Cookie(COOKIE, raw)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void logoutFromAnOriginOutsideTheAllowListIsForbiddenAndRevokesNothing() throws Exception {
        String raw = cookieValue(login(newUser()).andReturn());

        mockMvc.perform(post("/auth/logout").with(from(nextClient())).cookie(new Cookie(COOKIE, raw))
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(raw)).orElseThrow().getRevokedAt())
                .isNull();
    }

    @Test
    void refreshAcceptsAnAllowedOriginWithATrailingSlash() throws Exception {
        String raw = cookieValue(login(newUser()).andReturn());

        mockMvc.perform(post("/auth/refresh").with(from(nextClient())).cookie(new Cookie(COOKIE, raw))
                        .header(HttpHeaders.ORIGIN, ORIGIN + "/"))
                .andExpect(status().isOk());
    }

    // --- CORS credentials only where needed

    @Test
    void credentialsAreAllowedOnLoginRefreshAndLogoutOnly() throws Exception {
        for (String path : List.of("/auth/login", "/auth/refresh", "/auth/logout")) {
            preflight(path).andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                    .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        }
        for (String path : List.of("/auth/register", "/triage", "/me")) {
            preflight(path).andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }
    }

    // --- rate limit and public routes

    @Test
    void sixthRefreshFromTheSameClientIsTooManyRequests() throws Exception {
        String client = nextClient();
        for (int i = 0; i < 5; i++) {
            refresh(client, "unknown-" + i).andExpect(status().isUnauthorized());
        }

        refresh(client, "unknown-6")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void otherAuthRoutesStayClosed() throws Exception {
        mockMvc.perform(post("/auth/anything").with(from(nextClient())).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/refresh").with(from(nextClient())))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/auth/login").with(from(nextClient()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)));
    }

    private ResultActions refresh(String rawToken) throws Exception {
        return refresh(nextClient(), rawToken);
    }

    private ResultActions refresh(String client, String rawToken) throws Exception {
        var request = post("/auth/refresh").with(from(client)).header(HttpHeaders.ORIGIN, ORIGIN);
        if (rawToken != null) {
            request.cookie(new Cookie(COOKIE, rawToken));
        }
        return mockMvc.perform(request);
    }

    private ResultActions logout(String rawToken) throws Exception {
        var request = post("/auth/logout").with(from(nextClient())).header(HttpHeaders.ORIGIN, ORIGIN);
        if (rawToken != null) {
            request.cookie(new Cookie(COOKIE, rawToken));
        }
        return mockMvc.perform(request);
    }

    private ResultActions preflight(String path) throws Exception {
        return mockMvc.perform(options(path)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"));
    }

    // value of the refresh_token cookie set by the response
    private static String cookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).as("Set-Cookie").isNotNull();
        return setCookie.substring((COOKIE + "=").length(), setCookie.indexOf(';'));
    }

    private static long tokenLifetimeSeconds(String jwt) {
        String payload = new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
        long iat = ((Number) JsonPath.read(payload, "$.iat")).longValue();
        long exp = ((Number) JsonPath.read(payload, "$.exp")).longValue();
        return exp - iat;
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String nextClient() {
        return "10.3.0." + CLIENTS.incrementAndGet();
    }

    private String newUser() {
        User user = new User();
        user.setRealName("refresh test");
        user.setEmail("refresh-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(UserType.dependente);
        return userRepository.save(user).getEmail();
    }
}
