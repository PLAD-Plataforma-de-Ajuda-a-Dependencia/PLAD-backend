package plad.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plad.dto.AccessLogEntryDTO;
import plad.dto.ConsentRequestDTO;
import plad.dto.ErrorResponseDTO;
import plad.dto.MeResponseDTO;
import plad.dto.NicknameResponseDTO;
import plad.dto.PageResponseDTO;
import plad.services.AuditService;
import plad.services.ConsentService;
import plad.services.MeService;
import plad.services.NicknameService;

@Tag(name = "Me", description = "Data about the logged-in user's own data; requires a Bearer token")
@RestController
@RequestMapping("/me")
public class MeController {

    private final AuditService auditService;
    private final MeService meService;
    private final NicknameService nicknameService;
    private final ConsentService consentService;

    public MeController(AuditService auditService, MeService meService, NicknameService nicknameService,
                        ConsentService consentService) {
        this.consentService = consentService;
        this.nicknameService = nicknameService;
        this.auditService = auditService;
        this.meService = meService;
    }

    @Operation(summary = "My account",
            description = "Data of the logged-in user, for the front to pick the first screen after login. "
                    + "The user comes from the token, never from the request. Fields that do not apply to the user's type are null: "
                    + "has_triage and nickname for dependents; real_name, credential_type, crm_crp_number and is_validated for psychologists. "
                    + "consent_pending is set for every type: true until the terms and privacy policy in force are accepted (POST /me/consents)")
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

    @Operation(operationId = "rerollNickname", summary = "Draw a new anonymous nickname",
            description = "Dependents only. Replaces the nickname other users see by a new random one, keeps the old one in the history "
                    + "and writes a NICKNAME_REROLLED audit entry. At most 5 per day per dependent")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The new nickname",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = NicknameResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token, or the user no longer exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "The user is not a dependent",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "429", description = "The daily limit of 5 rerolls was reached",
                    headers = @Header(name = "Retry-After", description = "Seconds until the next reroll",
                            schema = @Schema(type = "integer")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PreAuthorize("hasRole('DEPENDENTE')")
    @PostMapping("/nickname/reroll")
    public ResponseEntity<NicknameResponseDTO> rerollNickname(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(new NicknameResponseDTO(nicknameService.reroll(userId)));
    }

    @Operation(operationId = "acceptConsents", summary = "Accept the terms and privacy policy in force",
            description = "Any account type. Records the acceptance of both documents in the versions in force (LGPD art. 11), "
                    + "with the time and the client IP. Use it when GET /me says consent_pending. "
                    + "Accepting a version that is already on file changes nothing")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Accepted (or already on file)"),
            @ApiResponse(responseCode = "400",
                    description = "Invalid body, or (CONSENT_REQUIRED) a version is missing or is not the one in force",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token, or the user no longer exists",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/consents")
    public ResponseEntity<Void> acceptConsents(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody ConsentRequestDTO request,
                                               HttpServletRequest httpRequest) {
        consentService.accept(userId, request.acceptedTermsVersion(), request.acceptedPrivacyVersion(),
                httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
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
