package plad.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class RequestIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void clientRequestIdIsReusedInTheResponse() throws Exception {
        mockMvc.perform(get("/does-not-exist").header("X-Request-Id", "client-id_1.2"))
                .andExpect(header().string("X-Request-Id", "client-id_1.2"));
    }

    @Test
    void missingRequestIdIsGenerated() throws Exception {
        String id = mockMvc.perform(get("/does-not-exist"))
                .andReturn().getResponse().getHeader("X-Request-Id");

        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void unsafeRequestIdIsReplaced() throws Exception {
        String tooLong = "a".repeat(65);
        for (String unsafe : new String[]{"has space", "bad\"quote", tooLong, "ação"}) {
            String id = mockMvc.perform(get("/does-not-exist").header("X-Request-Id", unsafe))
                    .andReturn().getResponse().getHeader("X-Request-Id");

            assertThat(id).isNotEqualTo(unsafe);
            assertThat(UUID.fromString(id)).isNotNull();
        }
    }

    @Test
    void unauthenticatedResponseAlsoCarriesTheId() throws Exception {
        mockMvc.perform(get("/triage/questions").header("X-Request-Id", "from-client"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "from-client"));
    }

    @Test
    void mdcIsClearedAfterTheRequest() throws Exception {
        mockMvc.perform(get("/does-not-exist").header("X-Request-Id", "cleanup-check"));

        assertThat(MDC.get("requestId")).isNull();
    }
}
