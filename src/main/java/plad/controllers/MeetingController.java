package plad.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plad.dto.ErrorResponseDTO;
import plad.dto.MeetingRequestDTO;
import plad.dto.MeetingResponseDTO;
import plad.services.MeetingService;

import java.net.URI;
import java.time.Instant;
import java.util.List;

// Psychologists manage their own meetings; every authenticated role reads them (the calendar).
@Tag(name = "Meetings", description = "Create and manage meetings (psychologist) and read the calendar (any role)")
@RestController
@RequestMapping("/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @Operation(operationId = "createMeeting", summary = "Create a meeting",
            description = "The start must be at least 60 minutes from now, the duration 15 to 180 minutes and the capacity "
                    + "2 to 30. The same psychologist cannot have two scheduled meetings that overlap "
                    + "(touching ones, ending when the next starts, are fine)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The meeting",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeetingResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (shape) or INVALID_MEETING (a limit, with the fields in errors)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "The user is not a psychologist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "MEETING_CONFLICT: overlaps another scheduled meeting of yours",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping
    @PreAuthorize("hasRole('PSICOLOGO')")
    public ResponseEntity<MeetingResponseDTO> create(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody MeetingRequestDTO request) {
        MeetingResponseDTO created = meetingService.create(userId, request);
        return ResponseEntity.created(URI.create("/meetings/" + created.id())).body(created);
    }

    @Operation(operationId = "updateMeeting", summary = "Edit a meeting",
            description = "Host only, while scheduled and before it starts. Same body and limits as the creation; "
                    + "the 60-minute lead time applies only when the start is moved")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The meeting",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeetingResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or INVALID_MEETING",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Not a psychologist, or not the host of this meeting",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "MEETING_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "MEETING_CONFLICT: not scheduled, already started, or overlaps another meeting of yours",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PSICOLOGO')")
    public ResponseEntity<MeetingResponseDTO> update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                                     @Valid @RequestBody MeetingRequestDTO request) {
        return ResponseEntity.ok(meetingService.update(userId, id, request));
    }

    @Operation(operationId = "cancelMeeting", summary = "Cancel a meeting",
            description = "Host only, while scheduled and before it ends. The meeting stays in the calendar as cancelled")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The meeting, now cancelled",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeetingResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Not a psychologist, or not the host of this meeting",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "MEETING_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "MEETING_CONFLICT: not scheduled or already ended",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PSICOLOGO')")
    public ResponseEntity<MeetingResponseDTO> cancel(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(meetingService.cancel(userId, id));
    }

    @Operation(operationId = "endMeeting", summary = "End a meeting",
            description = "Host only, while scheduled and once it has started")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The meeting, now ended",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeetingResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Not a psychologist, or not the host of this meeting",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "MEETING_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "MEETING_CONFLICT: not scheduled or not started yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('PSICOLOGO')")
    public ResponseEntity<MeetingResponseDTO> end(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(meetingService.end(userId, id));
    }

    @Operation(operationId = "listMeetings", summary = "List the meetings that start in a window",
            description = "Any authenticated role. Meetings whose start is in [from, to), ordered by start, cancelled and "
                    + "ended ones included. The window is at most 62 days")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The meetings",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = MeetingResponseDTO.class)))),
            @ApiResponse(responseCode = "400", description = "Missing/malformed from or to, or INVALID_MEETING (to not after from, or window over 62 days)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping
    public ResponseEntity<List<MeetingResponseDTO>> list(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Start of the window, inclusive (ISO-8601 instant)", example = "2026-11-01T03:00:00Z")
            @RequestParam(name = "from") Instant from,
            @Parameter(description = "End of the window, exclusive (ISO-8601 instant)", example = "2026-12-13T03:00:00Z")
            @RequestParam(name = "to") Instant to) {
        return ResponseEntity.ok(meetingService.list(userId, from, to));
    }

    @Operation(operationId = "findMeeting", summary = "Read one meeting")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The meeting",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeetingResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "MEETING_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponseDTO> find(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(meetingService.find(userId, id));
    }
}
