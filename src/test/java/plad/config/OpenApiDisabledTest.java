package plad.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// the test JVM turns the docs on (pom.xml); this context turns them back off, like prod
@SpringBootTest(properties = "springdoc.api-docs.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class OpenApiDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsRequireAuthenticationWhenDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUiRequiresAuthenticationWhenDisabled() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    }
}
