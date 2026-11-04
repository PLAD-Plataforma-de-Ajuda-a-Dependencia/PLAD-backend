package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

// both versions are optional in the schema on purpose: a missing one answers 400 CONSENT_REQUIRED, not VALIDATION_ERROR
public record ConsentRequestDTO(
        @Schema(description = "Version of the terms of use being accepted; must be the one in force, "
                + "otherwise 400 CONSENT_REQUIRED", example = "1.0")
        @Size(max = 20) String acceptedTermsVersion,
        @Schema(description = "Version of the privacy policy being accepted; must be the one in force, "
                + "otherwise 400 CONSENT_REQUIRED", example = "1.0")
        @Size(max = 20) String acceptedPrivacyVersion
) {}
