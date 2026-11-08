package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

// optional in the schema on purpose: a missing version answers 400 CONSENT_REQUIRED, not VALIDATION_ERROR
public record HealthConsentRequestDTO(
        @Schema(description = "Version of the health data consent being accepted; must be the one in force, "
                + "otherwise 400 CONSENT_REQUIRED", example = "1.0")
        @Size(max = 20) String acceptedHealthVersion
) {}
