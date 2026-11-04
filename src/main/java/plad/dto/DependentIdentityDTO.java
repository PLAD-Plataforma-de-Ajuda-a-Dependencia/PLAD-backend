package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// The only DTO that carries a dependent's real name. It is for psychologists only (admins get DependentNicknameDTO);
// anywhere else a dependent is shown by nickname only.
@Schema(description = "Who answered the triage, as seen by a psychologist")
public record DependentIdentityDTO(
        @Schema(description = "Anonymous nickname, the name every other user sees", example = "Morcego Sereno")
        String nickname,
        @Schema(description = "Real name, shown only to psychologists, together with the triage", example = "Maria Silva")
        String realName
) {
}
