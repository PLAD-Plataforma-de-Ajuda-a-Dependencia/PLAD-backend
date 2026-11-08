package plad.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plad.dto.ErrorResponseDTO;
import plad.dto.PageResponseDTO;
import plad.dto.TriageReadResponseDTO;
import plad.services.TriageReadService;

// Psychologists read triages here, with the dependent's nickname and real name, but only those of dependents registered
// in a meeting of theirs that was not cancelled. Admins read every triage at /admin/triages (nickname only,
// AdminTriageReadController). A dependent has no read route, not even for their own triage: they get their answers in
// the POST/PUT response.
@Tag(name = "Triage", description = "Reading triages; psychologist only, and only the dependents registered in a "
        + "non-cancelled meeting of theirs (admins read every triage at /admin/triages)")
@RestController
@RequestMapping("/triages")
@PreAuthorize("hasRole('PSICOLOGO')")
public class TriageReadController {

    private final TriageReadService triageReadService;

    public TriageReadController(TriageReadService triageReadService) {
        this.triageReadService = triageReadService;
    }

    // explicit operationIds: a second method called "list" would rename the one in /admin/audit in the contract
    @Operation(operationId = "listTriages", summary = "List triages, newest first",
            description = "Only the triages of dependents registered in a meeting of the caller that was not cancelled "
                    + "(scheduled or ended); with no such dependent the page is empty and nothing is recorded. "
                    + "Each triage returned is recorded in the audit trail (TRIAGE_VIEWED) with the reader's role. "
                    + "The dependent is identified by id, nickname and real name")
    @ApiResponses({
            // no explicit schema: springdoc reads the generic return type, so the items keep their type
            @ApiResponse(responseCode = "200", description = "One page of triages"),
            @ApiResponse(responseCode = "400", description = "Invalid page/size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "The user is not a psychologist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping
    public ResponseEntity<PageResponseDTO<TriageReadResponseDTO>> list(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Zero-based page number") @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(triageReadService.list(userId, page, size));
    }

    @Operation(operationId = "findTriage", summary = "Read one triage",
            description = "Only for a dependent registered in a meeting of the caller that was not cancelled; any other "
                    + "triage answers 404, as if it did not exist, and is not recorded. "
                    + "Recorded in the audit trail (TRIAGE_VIEWED) with the reader's role, every time it is read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The triage",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TriageReadResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "The user is not a psychologist",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "No triage with this id, or its dependent is not registered "
                    + "in a non-cancelled meeting of the caller (TRIAGE_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<TriageReadResponseDTO> find(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(triageReadService.find(userId, id));
    }
}
