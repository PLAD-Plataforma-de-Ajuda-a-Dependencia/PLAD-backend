package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// What a browser needs to open the live room. The token is a credential: the front end keeps it in memory only.
public record MeetingJoinResponseDTO(
        @Schema(description = "WebSocket address of the video room (wss://...)", example = "wss://plad.livekit.cloud")
        String serverUrl,
        @Schema(description = "Signed token that lets this person into this meeting's room only. Valid until 15 minutes "
                + "after the meeting ends; it carries the nickname (or the host's name), an opaque identity and the role, "
                + "and no e-mail, id or real name of a dependent")
        String token
) {}
