package pladBack.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pladBack.entity.User;
import pladBack.services.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring's own exceptions keep their status but answer with the same body as ours
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;

    @Test
    void unknownRouteIsNotFound() throws Exception {
        mockMvc.perform(get("/does-not-exist").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/triage/questions").header("Authorization", bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void malformedJsonIsBadRequestWithoutParserDetails() throws Exception {
        mockMvc.perform(post("/triage")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": ["))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Failed to read request"));
    }

    private String bearer() {
        User user = new User();
        user.setId(1L);
        return "Bearer " + jwtService.generateToken(user);
    }
}
