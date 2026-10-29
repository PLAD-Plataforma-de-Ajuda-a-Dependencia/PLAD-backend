package plad.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.CannotCreateTransactionException;
import plad.entity.User;
import plad.services.JwtService;
import plad.services.TriageService;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// a failing TriageService stands in for a database that blew up or went away
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ServiceUnavailableAndTraceTest {

    private static final String QUESTIONS = "/triage/questions";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private TriageService triageService;

    @Test
    void unexpectedFailureReturnsTheTraceThatAppearsInTheLog(CapturedOutput output) throws Exception {
        when(triageService.getQuestions()).thenThrow(new RuntimeException("secret detail"));

        MvcResult result = mockMvc.perform(get(QUESTIONS).header("Authorization", bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.trace").isNotEmpty())
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat(result.getResponse().getContentAsString())
                .contains("\"trace\":\"" + requestId + "\"")
                .contains("Erro interno. Informe o código " + requestId + " ao suporte.")
                .doesNotContain("secret detail");
        assertThat(output).contains("requestId=" + requestId, "method=GET", "route=" + QUESTIONS)
                .contains("[" + requestId + "]");
    }

    @Test
    void requestIdSentByTheClientBecomesTheTrace(CapturedOutput output) throws Exception {
        when(triageService.getQuestions()).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get(QUESTIONS).header("Authorization", bearer()).header("X-Request-Id", "client-req-42"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "client-req-42"))
                .andExpect(jsonPath("$.trace").value("client-req-42"));

        assertThat(output).contains("requestId=client-req-42");
    }

    @Test
    void otherErrorsHaveNoTrace() throws Exception {
        mockMvc.perform(get("/does-not-exist").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.trace").value((Object) null));
    }

    @Test
    void connectionFailureIsServiceUnavailable() throws Exception {
        expectUnavailable(new CannotGetJdbcConnectionException("db down", new SQLException("refused")));
    }

    @Test
    void transactionFailureIsServiceUnavailable() throws Exception {
        expectUnavailable(new CannotCreateTransactionException("Could not open JPA EntityManager"));
    }

    @Test
    void queryTimeoutIsServiceUnavailable() throws Exception {
        expectUnavailable(new QueryTimeoutException("too slow"));
    }

    @Test
    void poolTimeoutBehindAnotherWrapperIsServiceUnavailable() throws Exception {
        expectUnavailable(new JpaSystemException(new RuntimeException("wrapper",
                new SQLTransientConnectionException("Connection is not available, request timed out after 30000ms"))));
    }

    private void expectUnavailable(RuntimeException failure) throws Exception {
        when(triageService.getQuestions()).thenThrow(failure);

        mockMvc.perform(get(QUESTIONS).header("Authorization", bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.trace").value((Object) null));
    }

    private String bearer() {
        User user = new User();
        user.setId(1L);
        return "Bearer " + jwtService.generateToken(user);
    }
}
