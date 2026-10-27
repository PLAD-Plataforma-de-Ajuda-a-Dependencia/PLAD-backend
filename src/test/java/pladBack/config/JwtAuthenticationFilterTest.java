package pladBack.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pladBack.entity.User;
import pladBack.services.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Value("${jwt.secret}")
    private String secret;

    @Test
    void validTokenIsAuthenticated() throws Exception {
        getQuestions("Bearer " + jwtService.generateToken(userWithId(1L)))
                .andExpect(status().isOk());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/triage/questions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonBearerSchemeIsUnauthorized() throws Exception {
        getQuestions("Basic dXNlcjpwYXNz")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        getQuestions("Bearer not-a-jwt")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        String token = new JwtService("another-secret-0123456789-0123456789-0123456789", 60_000)
                .generateToken(userWithId(1L));

        getQuestions("Bearer " + token)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedTokenWithoutUserIdIsUnauthorized() throws Exception {
        String token = Jwts.builder().subject("1").signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();

        getQuestions("Bearer " + token)
                .andExpect(status().isUnauthorized());
    }

    private ResultActions getQuestions(String authorization) throws Exception {
        return mockMvc.perform(get("/triage/questions").header("Authorization", authorization));
    }

    private static User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
