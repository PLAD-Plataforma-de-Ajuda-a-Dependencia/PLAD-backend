package plad.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// What a browser needs to open the live room. The token is a credential: the front end keeps it in memory only, uses
// it to connect and drops it; the next entry asks for a new one.
public record MeetingJoinResponseDTO(
        @Schema(description = "WebSocket address of the video room (wss://...)", example = "wss://plad.livekit.cloud")
        String serverUrl,
        @Schema(description = "Signed token that lets this person into this meeting's room only. Valid for 2 minutes "
                + "from now: LiveKit checks it only on the first connection, so ask for a new one on every entry (also "
                + "after a lost connection or a reload) and never store it. It carries the nickname (or the host's "
                + "name), an opaque identity and the role, and no e-mail, id or real name of a dependent")
        String token,
        @Schema(description = "This person's identity in the room (the same as the token's subject): opaque, different "
                + "in every meeting", example = "q3ZxK9mR1v0nT7bYq3ZxK9mR1v0nT7bY")
        String identity,
        @Schema(description = "The host psychologist's identity in the room. Whoever has this identity is the "
                + "psychologist: decide it by this value, never by the name or the metadata of a participant. A "
                + "dependent sends the raised hand to it as a data message",
                example = "Xk2Lp8QwR5tYu1OaXk2Lp8QwR5tYu1Oa")
        String hostIdentity
) {}
