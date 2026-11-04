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
import plad.dto.AdminTriageReadResponseDTO;
import plad.dto.ErrorResponseDTO;
import plad.dto.PageResponseDTO;
import plad.services.TriageReadService;

// The admin's view of the triages: same data as /triages, but the dependent shows up by nickname only. The real
// name is for psychologists (TriageReadController); keeping the two routes and DTOs apart means no role check
// decides which field to hide.
@Tag(name = "Admin triage", description = "Reading triages as an admin; the dependent is shown by nickname only")
@RestController
@RequestMapping("/admin/triages")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTriageReadController {

    private final TriageReadService triageReadService;

    public AdminTriageReadController(TriageReadService triageReadService) {
        this.triageReadService = triageReadService;
    }

    @Operation(operationId = "listAdminTriages", summary = "List triages as an admin, newest first",
            description = "Each triage returned is recorded in the audit trail (TRIAGE_VIEWED) with the admin role. "
                    + "The dependent is identified by id and nickname; the real name is never sent")
    @ApiResponses({
            // no explicit schema: springdoc reads the generic return type, so the items keep their type
            @ApiResponse(responseCode = "200", description = "One page of triages"),
            @ApiResponse(responseCode = "400", description = "Invalid page/size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "The user is not an admin",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping
    public ResponseEntity<PageResponseDTO<AdminTriageReadResponseDTO>> list(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Zero-based page number") @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(triageReadService.listForAdmin(userId, page, size));
    }

    @Operation(operationId = "findAdminTriage", summary = "Read one triage as an admin",
            description = "Recorded in the audit trail (TRIAGE_VIEWED) with the admin role, every time it is read. "
                    + "The real name of the dependent is never sent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The triage",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AdminTriageReadResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "The user is not an admin",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "No triage with this id (TRIAGE_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<AdminTriageReadResponseDTO> find(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(triageReadService.findForAdmin(userId, id));
    }
}
