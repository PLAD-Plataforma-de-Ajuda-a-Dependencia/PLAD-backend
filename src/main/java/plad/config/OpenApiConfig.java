package plad.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
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
                                + "Errors use ErrorResponseDTO, except a missing or invalid token, which gets a bare 401.")
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
}
