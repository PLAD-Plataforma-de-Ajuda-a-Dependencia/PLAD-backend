package plad.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Value("${jwt.secret}")
    private String secret;

    @Test
    void validTokenIsAuthenticated() throws Exception {
        getQuestions("Bearer " + jwtService.generateToken(TestUsers.save(userRepository, UserType.dependente)))
                .andExpect(status().isOk());
    }

    // tokens issued before the version claim existed must log in again
    @Test
    void signedTokenWithoutTokenVersionIsUnauthorized() throws Exception {
        User user = TestUsers.save(userRepository, UserType.dependente);
        String token = Jwts.builder().subject(user.getId().toString()).claim("userId", user.getId())
                .claim("role", "dependente").signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();

        getQuestions("Bearer " + token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenOfADeletedUserIsUnauthorized() throws Exception {
        User user = TestUsers.save(userRepository, UserType.dependente);
        String token = jwtService.generateToken(user);
        userRepository.delete(user);

        getQuestions("Bearer " + token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/triage/questions"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void nonBearerSchemeIsUnauthorized() throws Exception {
        getQuestions("Basic dXNlcjpwYXNz")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        getQuestions("Bearer not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        String token = new JwtService("another-secret-0123456789-0123456789-0123456789", 60_000)
                .generateToken(userWithId(1L));

        getQuestions("Bearer " + token)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void signedTokenWithoutUserIdIsUnauthorized() throws Exception {
        String token = Jwts.builder().subject("1").signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();

        getQuestions("Bearer " + token)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // tokens issued before the role claim existed must log in again
    @Test
    void signedTokenWithoutRoleIsUnauthorized() throws Exception {
        String token = Jwts.builder().subject("1").claim("userId", 1L)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();

        getQuestions("Bearer " + token)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void signedTokenWithUnknownRoleIsUnauthorized() throws Exception {
        String token = Jwts.builder().subject("1").claim("userId", 1L).claim("role", "superuser")
                .signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();

        getQuestions("Bearer " + token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions getQuestions(String authorization) throws Exception {
        return mockMvc.perform(get("/triage/questions").header("Authorization", authorization));
    }

    private static User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        user.setTypeUser(UserType.dependente);
        return user;
    }
}
