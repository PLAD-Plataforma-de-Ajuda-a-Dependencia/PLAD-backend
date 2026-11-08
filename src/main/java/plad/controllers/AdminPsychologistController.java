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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plad.dto.AdminPsychologistResponseDTO;
import plad.dto.ErrorResponseDTO;
import plad.dto.PageResponseDTO;
import plad.services.PsychologistService;

// The admin's list of psychologists (decision 32): name, credential and validation state, never the e-mail.
// Read only: nothing here validates anyone yet (RF011).
@Tag(name = "Admin psychologists", description = "Listing psychologists as an admin; no e-mail and no dependent data")
@RestController
@RequestMapping("/admin/psychologists")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPsychologistController {

    private final PsychologistService psychologistService;

    public AdminPsychologistController(PsychologistService psychologistService) {
        this.psychologistService = psychologistService;
    }

    @Operation(operationId = "listAdminPsychologists", summary = "List psychologists as an admin, newest first",
            description = "Real name, credential type and number, validation state and sign-up date. "
                    + "The e-mail is never sent, and no data of any dependent is in the list")
    @ApiResponses({
            // no explicit schema: springdoc reads the generic return type, so the items keep their type
            @ApiResponse(responseCode = "200", description = "One page of psychologists"),
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
    public ResponseEntity<PageResponseDTO<AdminPsychologistResponseDTO>> list(
            @Parameter(description = "Zero-based page number") @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(psychologistService.findPageForAdmin(page, size));
    }
}
