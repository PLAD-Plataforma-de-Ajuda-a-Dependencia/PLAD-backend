package plad.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

// the 403 handler has no HTTP route yet (no role rules), so both handlers are exercised directly
class SecurityErrorHandlerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SecurityErrorHandler handler = new SecurityErrorHandler(jsonMapper);

    @Test
    void entryPointWritesUnauthorizedJson() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response, new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asString()).isNotBlank();
        assertThat(body.get("timestamp").asString()).isNotBlank();
        assertThat(body.get("errors").isArray()).isTrue();
        assertThat(body.get("errors")).isEmpty();
    }

    @Test
    void accessDeniedHandlerWritesForbiddenJson() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(body.get("message").asString()).isNotBlank();
        assertThat(body.get("errors")).isEmpty();
    }

    @Test
    void bodyDoesNotEchoTheSecurityExceptionMessage() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response, new BadCredentialsException("internal detail"));

        assertThat(response.getContentAsString()).doesNotContain("internal detail");
    }
}
