package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record FieldErrorDTO(
    @Schema(description = "Request field that failed validation, in snake_case", example = "answers[0].values")
    String field,
    @Schema(description = "Why the field was rejected", example = "must not be empty")
    String message
) {}
