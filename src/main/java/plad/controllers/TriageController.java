package plad.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plad.dto.ErrorResponseDTO;
import plad.dto.QuestionResponseDTO;
import plad.dto.TriageRequestDTO;
import plad.dto.TriageResponseDTO;
import plad.services.TriageService;

import java.util.List;

// a missing or invalid token gets a bare 401 (no body) from SecurityConfig, before reaching these methods
@Tag(name = "Triage", description = "Dependents' one-time triage questionnaire; requires a Bearer token")
@RestController
@RequestMapping("/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @Operation(summary = "List the triage questions in display order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Questions",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = QuestionResponseDTO.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token (empty body)", content = @Content)
    })
    @GetMapping("/questions")
    public ResponseEntity<List<QuestionResponseDTO>> getQuestions() {
        return ResponseEntity.ok(triageService.getQuestions());
    }

    @Operation(summary = "Submit the triage", description = "A dependent can submit once; use PUT to change it afterwards")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Triage stored",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TriageResponseDTO.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid body, answer without question_id or question_code (or with ones that disagree), unknown or repeated question, missing required question or invalid option",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token (empty body)", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user is not a dependent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Triage already submitted",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping
    public ResponseEntity<TriageResponseDTO> submit(@AuthenticationPrincipal Long userId,
                                                    @Valid @RequestBody TriageRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(triageService.submit(userId, request));
    }

    @Operation(summary = "Replace the submitted triage")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Triage replaced",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TriageResponseDTO.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid body, answer without question_id or question_code (or with ones that disagree), unknown or repeated question, missing required question or invalid option",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token (empty body)", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user is not a dependent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "No triage submitted yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PutMapping
    public ResponseEntity<TriageResponseDTO> resubmit(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody TriageRequestDTO request) {
        return ResponseEntity.ok(triageService.resubmit(userId, request));
    }
}
