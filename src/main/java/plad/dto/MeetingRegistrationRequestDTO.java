package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

// optional in the schema on purpose: a missing version (or a missing body) answers 400 CONSENT_REQUIRED, not VALIDATION_ERROR
public record MeetingRegistrationRequestDTO(
        @Schema(description = "Version of the participation term shown at the registration; must be the one in force, "
                + "otherwise 400 CONSENT_REQUIRED and nothing is recorded", example = "1.0")
        @Size(max = 20) String acceptedParticipationVersion
) {}
