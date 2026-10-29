package plad.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import plad.dto.AnswerRequestDTO;
import plad.dto.AnswerResponseDTO;
import plad.dto.ErrorResponseDTO;
import plad.dto.FieldErrorDTO;
import plad.dto.LoginRequestDTO;
import plad.dto.LoginResponseDTO;
import plad.dto.OptionDTO;
import plad.dto.QuestionResponseDTO;
import plad.dto.RegisterRequestDTO;
import plad.dto.TriageRequestDTO;
import plad.dto.TriageResponseDTO;
import plad.dto.UserResponseDTO;
import plad.entity.CrmCrpType;
import plad.entity.QuestionType;
import plad.entity.Sex;
import plad.entity.UserType;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// docs/openapi.json is the versioned API contract: any change to it must be a deliberate, reviewed diff
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class OpenApiContractTest {

    private static final Path SNAPSHOT = Path.of("docs", "openapi.json");
    private static final String CONTRACT_CHANGED =
            "contrato mudou: rode ./mvnw test -Dopenapi.update=true e commite docs/openapi.json";

    // fixed key order, indentation and line feeds: the snapshot must not depend on the OS or the JVM
    private static final JsonMapper NORMALIZER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .defaultPrettyPrinter(new DefaultPrettyPrinter(
                            Separators.createDefaultInstance().withObjectNameValueSpacing(Separators.Spacing.AFTER))
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n")))
            .build();

    @Autowired
    private MockMvc mockMvc;
    // the mapper that serializes real responses (Jackson 3, spring.jackson.* applied)
    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void apiDocsMatchCommittedSnapshot() throws Exception {
        String actual = normalize(fetchApiDocs());

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.exists(SNAPSHOT)) {
            fail(CONTRACT_CHANGED);
        }
        // a Windows checkout may turn the file into CRLF (core.autocrlf)
        String expected = Files.readString(SNAPSHOT, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(expected, actual, CONTRACT_CHANGED);
    }

    @Test
    void swaggerUiIsPublicWhenEnabled() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    // springdoc builds schemas with its own Jackson 2 mapper, so the naming strategy lives in two places
    @Test
    void schemaPropertiesMatchRuntimeSerialization() throws Exception {
        JsonNode schemas = jsonMapper.readTree(fetchApiDocs()).path("components").path("schemas");

        requestSamples().forEach((name, dto) -> assertSchemaMatches(schemas, name, dto));
        responseSamples().forEach((name, dto) -> assertSchemaMatches(schemas, name, dto));
    }

    // a new DTO must not skip the check above: its schema needs a sample in requestSamples() or responseSamples()
    @Test
    void everySchemaHasARuntimeSample() throws Exception {
        JsonNode schemas = jsonMapper.readTree(fetchApiDocs()).path("components").path("schemas");

        Set<String> withoutSample = new TreeSet<>(schemas.propertyNames());
        withoutSample.removeAll(requestSamples().keySet());
        withoutSample.removeAll(responseSamples().keySet());
        assertThat(withoutSample)
                .as("schemas sem amostra: adicione o DTO em requestSamples() ou responseSamples() de OpenApiContractTest")
                .isEmpty();
    }

    // the API sends every key of a response DTO, so the contract must say so (openapi-typescript reads "required")
    @Test
    void responseSchemasRequireEveryProperty() throws Exception {
        JsonNode schemas = jsonMapper.readTree(fetchApiDocs()).path("components").path("schemas");

        responseSamples().forEach((name, dto) -> {
            Set<String> required = new TreeSet<>();
            schemas.path(name).path("required").forEach(property -> required.add(property.asString()));
            Set<String> serialized = new TreeSet<>(jsonMapper.valueToTree(dto).propertyNames());
            assertThat(required).as("required properties of schema %s", name).isEqualTo(serialized);
        });
    }

    // fully populated on purpose: a null field would still be serialized, but a sample should show every key
    private static Map<String, Object> requestSamples() {
        Map<String, Object> samples = new TreeMap<>();
        samples.put("AnswerRequestDTO", new AnswerRequestDTO(1L, "AGE_RANGE", List.of("AGE_25_39")));
        samples.put("LoginRequestDTO", new LoginRequestDTO("a@b.com", "password-123"));
        samples.put("RegisterRequestDTO", new RegisterRequestDTO(
                "name", "a@b.com", "password-123", LocalDate.of(1990, 5, 20), Sex.nao_binario,
                UserType.psicologo, CrmCrpType.CRP, "06/123456", "Psicologia"));
        samples.put("TriageRequestDTO", new TriageRequestDTO(List.of(new AnswerRequestDTO(1L, "AGE_RANGE", List.of("AGE_25_39")))));
        return samples;
    }

    private static Map<String, Object> responseSamples() {
        Map<String, Object> samples = new TreeMap<>();
        AnswerResponseDTO answer = new AnswerResponseDTO(1L, "AGE_RANGE", "text", List.of("AGE_25_39"));
        samples.put("AnswerResponseDTO", answer);
        samples.put("ErrorResponseDTO", new ErrorResponseDTO("CODE", "message", Instant.now(),
                List.of(new FieldErrorDTO("field", "message")), "trace-id"));
        samples.put("FieldErrorDTO", new FieldErrorDTO("field", "message"));
        samples.put("LoginResponseDTO", new LoginResponseDTO("token"));
        samples.put("OptionDTO", new OptionDTO("code", "label"));
        samples.put("QuestionResponseDTO", new QuestionResponseDTO(
                1L, "AGE_RANGE", "text", QuestionType.single_choice, List.of(new OptionDTO("code", "label")), (short) 1, true));
        samples.put("TriageResponseDTO", new TriageResponseDTO(1L, Instant.now(), List.of(answer)));
        samples.put("UserResponseDTO", new UserResponseDTO(1L, "name", "a@b.com"));
        return samples;
    }

    private void assertSchemaMatches(JsonNode schemas, String name, Object dto) {
        Set<String> documented = new TreeSet<>(schemas.path(name).path("properties").propertyNames());
        Set<String> serialized = new TreeSet<>(jsonMapper.valueToTree(dto).propertyNames());
        assertThat(documented).as("properties of schema %s", name).isEqualTo(serialized);
    }

    private String fetchApiDocs() throws Exception {
        // no token: the docs are public while springdoc.api-docs.enabled=true
        return mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String normalize(String json) throws IOException {
        Object tree = NORMALIZER.readValue(json, Object.class);
        return NORMALIZER.writeValueAsString(tree) + "\n";
    }
}
