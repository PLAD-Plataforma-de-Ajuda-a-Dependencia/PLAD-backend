package plad.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// springdoc switches its whole configuration off (ObjectMapperProvider included) when the docs are disabled, so
// these beans must go with it or the app would not start in prod
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                // fixed version on purpose: the pom version would change docs/openapi.json on every bump
                .info(new Info()
                        .title("PLAD API")
                        .description("Backend of the Plataforma de Ajuda a Dependência. JSON fields are snake_case. "
                                + "Every error, including a missing or invalid token (401), uses ErrorResponseDTO.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // public endpoints opt out with an empty @SecurityRequirements
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    // springdoc builds schemas with swagger-core's own Jackson 2 mapper and ignores spring.jackson.*, while the API
    // serializes with Spring's Jackson 3 mapper (SNAKE_CASE). Copy: the shared mapper also writes the OpenAPI
    // document itself, whose keys (operationId, requestBody...) must stay camelCase.
    @Bean
    public ModelResolver snakeCaseModelResolver(ObjectMapperProvider objectMapperProvider) {
        ObjectMapper mapper = objectMapperProvider.jsonMapper().copy();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(mapper);
    }

    // tags come in controller scan order, which is not the same on every OS; a stable order keeps docs/openapi.json stable
    @Bean
    public OpenApiCustomizer sortedTags() {
        return openApi -> {
            if (openApi.getTags() != null) {
                openApi.getTags().sort(Comparator.comparing(Tag::getName));
            }
        };
    }

    // A response always carries every key of its DTO (nulls are serialized, not omitted), but springdoc only fills
    // "required" from Bean Validation, so openapi-typescript would type every response field as optional. A field that
    // can be null stays required and gets @Schema(nullable = true) on the DTO, which swagger-core renders as a
    // type with "null". A DTO used as both request and response would come out all required.
    @Bean
    public OpenApiCustomizer requiredResponseProperties() {
        return openApi -> {
            if (openApi.getPaths() == null || openApi.getComponents() == null
                    || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            Set<String> responseSchemas = new TreeSet<>();
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) {
                    return;
                }
                operation.getResponses().values().forEach(response -> {
                    if (response.getContent() != null) {
                        response.getContent().values()
                                .forEach(media -> collectSchemaRefs(media.getSchema(), schemas, responseSchemas));
                    }
                });
            }));
            responseSchemas.forEach(name -> {
                Schema<?> schema = schemas.get(name);
                if (schema != null && schema.getProperties() != null) {
                    schema.getProperties().keySet().forEach(schema::addRequiredItem);
                    schema.getProperties().values().forEach(OpenApiConfig::renderNullable);
                }
            });
        };
    }

    // The document is OpenAPI 3.1, whose serializer drops "nullable"; there a nullable field is a type list with
    // "null". Our ModelResolver builds 3.0-style schemas (single "type"), so the list is assembled here.
    private static void renderNullable(Schema<?> property) {
        if (Boolean.TRUE.equals(property.getNullable())) {
            if (property.getTypes() == null && property.getType() != null) {
                property.addType(property.getType());
            }
            property.addType("null");
        }
    }

    // follows $ref, array items and properties, so nested DTOs (OptionDTO, AnswerResponseDTO) are found too
    private static void collectSchemaRefs(Schema<?> schema, Map<String, Schema> schemas, Set<String> found) {
        if (schema == null) {
            return;
        }
        String ref = schema.get$ref();
        if (ref != null) {
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            if (found.add(name)) {
                collectSchemaRefs(schemas.get(name), schemas, found);
            }
            return;
        }
        collectSchemaRefs(schema.getItems(), schemas, found);
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(property -> collectSchemaRefs(property, schemas, found));
        }
    }

    // GlobalExceptionHandler.handleUnexpected answers any unhandled failure, so every operation can return it
    @Bean
    public OperationCustomizer internalServerErrorResponse() {
        return (operation, handlerMethod) -> {
            if (operation.getResponses() != null && !operation.getResponses().containsKey("500")) {
                operation.getResponses().addApiResponse("500", new ApiResponse()
                        .description("Unexpected error")
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ErrorResponseDTO")))));
            }
            return operation;
        };
    }

    // GlobalExceptionHandler.handleDatabaseUnavailable answers a database that is down or too slow, on any operation
    @Bean
    public OperationCustomizer serviceUnavailableResponse() {
        return (operation, handlerMethod) -> {
            if (operation.getResponses() != null && !operation.getResponses().containsKey("503")) {
                operation.getResponses().addApiResponse("503", new ApiResponse()
                        .description("Database unavailable, try again after Retry-After")
                        .addHeaderObject("Retry-After", new Header()
                                .description("Seconds until the next attempt")
                                .schema(new IntegerSchema()))
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ErrorResponseDTO")))));
            }
            return operation;
        };
    }
}
