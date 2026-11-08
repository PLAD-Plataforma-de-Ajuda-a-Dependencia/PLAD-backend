package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record SpeakPermissionRequestDTO(
        @Schema(description = "true lets the participant publish audio and video; false takes it away (and cuts what "
                + "they are publishing)", example = "true")
        @NotNull
        Boolean allowed
) {}
