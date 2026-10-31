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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plad.dto.AccessLogEntryDTO;
import plad.dto.ErrorResponseDTO;
import plad.dto.MeResponseDTO;
import plad.dto.PageResponseDTO;
import plad.services.AuditService;
import plad.services.MeService;

@Tag(name = "Me", description = "Data about the logged-in user's own data; requires a Bearer token")
@RestController
@RequestMapping("/me")
public class MeController {

    private final AuditService auditService;
    private final MeService meService;

    public MeController(AuditService auditService, MeService meService) {
        this.auditService = auditService;
        this.meService = meService;
    }

    @Operation(summary = "My account",
            description = "Data of the logged-in user, for the front to pick the first screen after login. "
                    + "The user comes from the token, never from the request. Fields that do not apply to the user's type are null: "
                    + "has_triage for dependents; real_name, credential_type, crm_crp_number and is_validated for psychologists")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The user's own data",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token, or the user no longer exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @GetMapping
    public ResponseEntity<MeResponseDTO> me(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(meService.findMe(userId));
    }

    @Operation(summary = "When someone else read my triage, and in which role",
            description = "Transparency to the data subject (LGPD art. 18). Shows the role of who read, never their name. Newest first")
    @ApiResponses({
            // no explicit schema: springdoc reads the generic return type, so the items keep their type
            @ApiResponse(responseCode = "200", description = "One page of access entries"),
            @ApiResponse(responseCode = "400", description = "Invalid page/size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "The user is not a dependent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PreAuthorize("hasRole('DEPENDENTE')")
    @GetMapping("/access-log")
    public ResponseEntity<PageResponseDTO<AccessLogEntryDTO>> accessLog(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Zero-based page number") @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(auditService.findAccessLog(userId, page, size));
    }
}
