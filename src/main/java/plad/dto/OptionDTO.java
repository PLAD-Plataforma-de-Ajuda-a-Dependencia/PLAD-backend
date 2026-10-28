package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OptionDTO(
        @Schema(description = "Value to send in answers[].values", example = "AGE_25_39")
        String code,
        @Schema(description = "Text to show to the user", example = "25 to 39")
        String label
) {
}
