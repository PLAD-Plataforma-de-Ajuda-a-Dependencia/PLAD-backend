package plad.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private static Schema<?> ref(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }

    private static Content json(Schema<?> schema) {
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }

    // ErrorResponseDTO.trace is the only real nullable field, so this stays the place that exercises the rule in isolation
    @Test
    void requiresEveryPropertyOfResponseSchemasAndLeavesRequestSchemasAlone() {
        Schema<?> nested = new ObjectSchema().addProperty("code", new StringSchema());
        Schema<?> response = new ObjectSchema()
                .addProperty("name", new StringSchema())
                .addProperty("note", new StringSchema().nullable(true))
                .addProperty("items", new ArraySchema().items(ref("Nested")));
        Schema<?> request = new ObjectSchema()
                .addProperty("x", new StringSchema())
                .addProperty("y", new StringSchema());
        request.addRequiredItem("x");
        OpenAPI openApi = new OpenAPI()
                .components(new Components()
                        .addSchemas("Nested", nested)
                        .addSchemas("Req", request)
                        .addSchemas("Resp", response))
                .paths(new Paths().addPathItem("/things", new PathItem().post(new Operation()
                        .requestBody(new RequestBody().content(json(ref("Req"))))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse().content(json(ref("Resp"))))
                                // a bare 401 has no content
                                .addApiResponse("401", new ApiResponse())))));

        new OpenApiConfig().requiredResponseProperties().customise(openApi);

        assertThat(response.getRequired()).containsExactly("items", "name", "note");
        assertThat(response.getProperties().get("note").getNullable()).isTrue();
        assertThat(response.getProperties().get("note").getTypes()).containsExactly("string", "null");
        assertThat(response.getProperties().get("name").getTypes()).containsExactly("string");
        assertThat(nested.getRequired()).containsExactly("code");
        assertThat(request.getRequired()).containsExactly("x");
    }
}
