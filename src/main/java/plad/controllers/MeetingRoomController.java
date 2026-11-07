package plad.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plad.dto.ErrorResponseDTO;
import plad.dto.MeetingJoinResponseDTO;
import plad.dto.SpeakPermissionRequestDTO;
import plad.services.MeetingModerationService;
import plad.services.MeetingRoomService;

// The live room of a meeting (RF005) and its moderation (RF006). The video itself runs on LiveKit; these routes only
// decide who may enter and who may speak.
@Tag(name = "Meeting room", description = "Enter the live room of a meeting (host or registered dependent) and "
        + "moderate it (host)")
@RestController
@RequestMapping("/meetings/{id}")
public class MeetingRoomController {

    private final MeetingRoomService meetingRoomService;
    private final MeetingModerationService meetingModerationService;

    public MeetingRoomController(MeetingRoomService meetingRoomService,
                                 MeetingModerationService meetingModerationService) {
        this.meetingRoomService = meetingRoomService;
        this.meetingModerationService = meetingModerationService;
    }

    @Operation(operationId = "joinMeeting", summary = "Enter the live room of a meeting",
            description = "The host psychologist or a dependent registered in the meeting, while it is scheduled, from "
                    + "10 minutes before it starts until it ends. Answers the server address and a token for this "
                    + "meeting's room only, valid until 15 minutes after the meeting ends. The token carries the "
                    + "dependent's nickname (the host's name for the psychologist), an opaque identity that is "
                    + "different in every meeting, and the role; nothing else. A dependent comes in without "
                    + "microphone or camera until the host allows them to speak. Ask for a new token on each entry")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Where and how to connect",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeetingJoinResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Not the host and not registered in this meeting "
                    + "(another psychologist, an admin, a dependent who is not in it, or one who was removed)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "MEETING_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "MEETING_NOT_OPEN: not scheduled (cancelled or ended), too early "
                    + "(more than 10 minutes before the start) or already over",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/join")
    public ResponseEntity<MeetingJoinResponseDTO> join(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(meetingRoomService.join(userId, id));
    }

    @Operation(operationId = "setParticipantSpeech", summary = "Allow or take away a participant's microphone and camera",
            description = "Host only. The participant is named by the identity from GET /meetings/{id}/participants; it "
                    + "must belong to a dependent registered in this meeting, otherwise 404 and nothing is sent to the "
                    + "video provider. Allowing lets them publish audio and video (not the screen); taking it away cuts "
                    + "what they are publishing at once. Chat and the raised hand are not affected")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Done"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: allowed is missing",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Not a psychologist, or not the host of this meeting",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "MEETING_NOT_FOUND, or MEETING_PARTICIPANT_NOT_FOUND (the identity "
                    + "is not a dependent registered in this meeting, or is not connected to the room)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "MEETING_NOT_OPEN: the meeting is not scheduled",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "503", headers = @Header(name = "Retry-After", description = "Seconds until the next attempt",
                    schema = @Schema(type = "integer")),
                    description = "ROOM_UNAVAILABLE: the video provider did not answer (with "
                    + "Retry-After). Nothing changed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/participants/{identity}/speak")
    @PreAuthorize("hasRole('PSICOLOGO')")
    public ResponseEntity<Void> speak(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                      @PathVariable String identity,
                                      @Valid @RequestBody SpeakPermissionRequestDTO request) {
        meetingModerationService.setSpeech(userId, id, identity, request.allowed());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "removeMeetingParticipant", summary = "Remove a participant from the meeting",
            description = "Host only. Drops the dependent from the room, invalidates the token they hold and takes their "
                    + "registration away: they cannot come back by asking for a new token (POST /join answers 403) and "
                    + "the seat is free. Works also for someone registered who is not connected. The identity is checked "
                    + "like in the speak route")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The dependent is out of the room and of the meeting"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Not a psychologist, or not the host of this meeting",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "MEETING_NOT_FOUND, or MEETING_PARTICIPANT_NOT_FOUND (the identity "
                    + "is not a dependent registered in this meeting)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "MEETING_NOT_OPEN: the meeting is not scheduled",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "503", headers = @Header(name = "Retry-After", description = "Seconds until the next attempt",
                    schema = @Schema(type = "integer")),
                    description = "ROOM_UNAVAILABLE: the video provider did not answer (with "
                    + "Retry-After). Nothing changed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/participants/{identity}/remove")
    @PreAuthorize("hasRole('PSICOLOGO')")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                       @PathVariable String identity) {
        meetingModerationService.remove(userId, id, identity);
        return ResponseEntity.noContent().build();
    }
}
