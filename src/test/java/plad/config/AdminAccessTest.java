package plad.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// the probe is a nested class so the component scan of other tests (and the OpenAPI snapshot) never sees it
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, AdminAccessTest.Probe.class})
class AdminAccessTest {

    @RestController
    static class Probe {

        // covered by the /admin/** rule in SecurityConfig
        @GetMapping("/admin/ping")
        String ping() {
            return "pong";
        }

        // outside /admin/**: only method security protects it
        @GetMapping("/probe/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "pong";
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;

    @Test
    void dependentGetsForbiddenOnAdminRoutes() throws Exception {
        forbidden(call("/admin/ping", UserType.dependente));
    }

    @Test
    void psychologistGetsForbiddenOnAdminRoutes() throws Exception {
        forbidden(call("/admin/ping", UserType.psicologo));
    }

    @Test
    void adminReachesAdminRoutes() throws Exception {
        call("/admin/ping", UserType.admin).andExpect(status().isOk());
    }

    @Test
    void anonymousGetsUnauthorizedOnAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // without a handler for AccessDeniedException the generic Exception handler would answer 500 here
    @Test
    void dependentGetsForbiddenOnMethodSecuredRoute() throws Exception {
        forbidden(call("/probe/admin-only", UserType.dependente));
    }

    @Test
    void psychologistGetsForbiddenOnMethodSecuredRoute() throws Exception {
        forbidden(call("/probe/admin-only", UserType.psicologo));
    }

    @Test
    void adminReachesMethodSecuredRoute() throws Exception {
        call("/probe/admin-only", UserType.admin).andExpect(status().isOk());
    }

    @Test
    void roleDoesNotBreakRoutesOpenToEveryone() throws Exception {
        for (UserType type : UserType.values()) {
            call("/triage/questions", type).andExpect(status().isOk());
        }
    }

    private ResultActions call(String path, UserType type) throws Exception {
        User user = TestUsers.save(userRepository, type);
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + jwtService.generateToken(user)));
    }

    private static void forbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
