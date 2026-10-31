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
import plad.dto.AuditLogResponseDTO;
import plad.dto.ErrorResponseDTO;
import plad.dto.PageResponseDTO;
import plad.entity.AuditAction;
import plad.services.AuditService;

// /admin/** already requires ROLE_ADMIN in SecurityConfig; @PreAuthorize keeps the rule if the route ever moves
@Tag(name = "Audit", description = "Audit trail of access to sensitive data; admin only")
@RestController
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Operation(summary = "List audit entries, newest first",
            description = "Entries hold only ids and state changes, never answers, names, e-mails or free text")
    @ApiResponses({
            // no explicit schema: springdoc reads the generic return type, so the items keep their type
            @ApiResponse(responseCode = "200", description = "One page of audit entries"),
            @ApiResponse(responseCode = "400", description = "Unknown action or invalid page/size",
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
    public ResponseEntity<PageResponseDTO<AuditLogResponseDTO>> list(
            @Parameter(description = "Only entries about this user") @RequestParam(name = "subject_user_id", required = false) Long subjectUserId,
            @Parameter(description = "Only this action") @RequestParam(name = "action", required = false) AuditAction action,
            @Parameter(description = "Zero-based page number") @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100") @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(auditService.findPage(subjectUserId, action, page, size));
    }
}
