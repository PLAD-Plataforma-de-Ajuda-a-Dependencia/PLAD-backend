package plad.config;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.UserRepository;
import plad.services.AdminBootstrapService;
import plad.services.JwtService;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// uses the shared default context on purpose: every distinct context keeps its own connection pool open on the
// one throwaway Postgres, and one more would run it out of connections
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminBootstrapRunnerTest {

    private static final String PASSWORD = "senha-admin-123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminBootstrapService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DependentRepository dependentRepository;
    @Autowired
    private PsychologistRepository psychologistRepository;
    @Autowired
    private JwtService jwtService;

    @Test
    void createsTheAdminWithoutDependentOrPsychologistRow() {
        String email = newEmail();

        service.createIfAbsent(email.toUpperCase(Locale.ROOT), PASSWORD);

        User admin = userRepository.findByEmail(email).orElseThrow();
        assertThat(admin.getTypeUser()).isEqualTo(UserType.admin);
        assertThat(dependentRepository.existsById(admin.getId())).isFalse();
        assertThat(psychologistRepository.existsById(admin.getId())).isFalse();
    }

    @Test
    void runningAgainKeepsTheFirstAdminAndItsPassword() throws Exception {
        String email = newEmail();
        service.createIfAbsent(email, PASSWORD);
        long before = userRepository.count();

        service.createIfAbsent(email, "another-password-456");

        assertThat(userRepository.count()).isEqualTo(before);
        login("10.9.0.1", email, PASSWORD).andExpect(status().isOk());
        login("10.9.0.2", email, "another-password-456").andExpect(status().isUnauthorized());
    }

    @Test
    void adminLogsInWithTheRoleClaimAndPassesTheAdminRule() throws Exception {
        String email = newEmail();
        service.createIfAbsent(email, PASSWORD);

        String response = login("10.9.0.3", email, PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.token");

        assertThat(jwtService.parse(token).role()).isEqualTo(UserType.admin);
        // no controller serves /admin yet: past the authorization rule the answer is 404, not 401/403
        mockMvc.perform(get("/admin/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private ResultActions login(String client, String email, String password)
            throws Exception {
        return mockMvc.perform(post("/auth/login")
                .with(request -> {
                    request.setRemoteAddr(client);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password)));
    }

    private static String newEmail() {
        return "admin-" + UUID.randomUUID() + "@example.com";
    }
}
