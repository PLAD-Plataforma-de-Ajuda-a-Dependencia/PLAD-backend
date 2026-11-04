package plad.controllers;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plad.dto.ErrorResponseDTO;
import plad.dto.RegisterRequestDTO;
import plad.dto.UserResponseDTO;
import plad.services.AuthService;

@Tag(name = "Auth", description = "Public endpoints, rate limited to 5 requests per minute per client IP")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a dependent or a psychologist")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "400",
                    description = "Invalid body (including a birth date under 18 years old), type_user admin, a psychologist without credentials "
                            + "or with a credential number already registered, or (CONSENT_REQUIRED) the terms and the privacy policy "
                            + "in force were not accepted: accepted_terms_version and accepted_privacy_version missing or not the current ones",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "E-mail already in use",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    headers = @Header(name = "Retry-After", description = "Seconds until the next attempt",
                            schema = @Schema(type = "integer")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request, HttpServletRequest httpRequest) {
        // getRemoteAddr() is the real client IP: Tomcat only honors X-Forwarded-For from trusted proxies
        UserResponseDTO response = authService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }
}
