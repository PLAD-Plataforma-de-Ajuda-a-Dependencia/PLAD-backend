package plad.config;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import plad.dto.ErrorResponseDTO;
import plad.entity.User;
import plad.services.JwtService;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Spring's own exceptions keep their status but answer with the same body as ours
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String EMAIL = "maria.silva@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private GlobalExceptionHandler handler;

    @Test
    void unknownRouteIsNotFound() throws Exception {
        mockMvc.perform(get("/does-not-exist").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/triage/questions").header("Authorization", bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void malformedJsonIsBadRequestWithoutParserDetails() throws Exception {
        mockMvc.perform(post("/triage")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": ["))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Failed to read request"));
    }

    // the Postgres message carries the duplicated value (the user's e-mail); the log may only name the constraint
    @Test
    void dataIntegrityLogNamesTheConstraintButNotTheDuplicatedEmail(CapturedOutput output) {
        String pgMessage = "ERROR: duplicate key value violates unique constraint \"users_email_key\"\n"
                + "  Detail: Key (email)=(" + EMAIL + ") already exists.";
        SQLException sqlException = new SQLException(pgMessage);
        ConstraintViolationException hibernateCause =
                new ConstraintViolationException(pgMessage, sqlException, ConstraintKind.UNIQUE, "users_email_key");

        ResponseEntity<ErrorResponseDTO> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException(pgMessage, hibernateCause));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("DATA_CONFLICT");
        assertThat(output).contains("users_email_key");
        assertThat(output).doesNotContain(EMAIL);
    }

    @Test
    void dataIntegrityLogWithoutHibernateCauseDoesNotLeakTheMessage(CapturedOutput output) {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("Detail: Key (email)=(" + EMAIL + ") already exists.");

        ResponseEntity<ErrorResponseDTO> response = handler.handleDataIntegrity(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(output).contains("unknown");
        assertThat(output).doesNotContain(EMAIL);
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutItsMessage() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleUnexpected(new RuntimeException("secret detail"), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("secret detail");
        assertThat(response.getBody().errors()).isEmpty();
        assertThat(response.getBody().trace()).isNotBlank();
        assertThat(response.getBody().message())
                .isEqualTo("Erro interno. Informe o código " + response.getBody().trace() + " ao suporte.");
    }

    private String bearer() {
        User user = new User();
        user.setId(1L);
        return "Bearer " + jwtService.generateToken(user);
    }
}
