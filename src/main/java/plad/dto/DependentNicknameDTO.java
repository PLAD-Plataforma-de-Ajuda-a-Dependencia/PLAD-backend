package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// A dependent as every reader except a psychologist sees them: by nickname only, never the real name.
@Schema(description = "Who answered the triage, as seen by an admin: the anonymous nickname only")
public record DependentNicknameDTO(
        @Schema(description = "Anonymous nickname, the name every other user sees", example = "Morcego Sereno")
        String nickname
) {
}
