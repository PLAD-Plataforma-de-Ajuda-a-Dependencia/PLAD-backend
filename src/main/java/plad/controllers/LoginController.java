package plad.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import plad.config.RefreshCookieFactory;
import plad.dto.ErrorResponseDTO;
import plad.dto.LoginRequestDTO;
import plad.dto.LoginResponseDTO;
import plad.exception.InvalidRefreshTokenException;
import plad.services.LoginService;
import plad.services.RefreshTokenService;

// login and refresh are rate limited by config/RateLimitInterceptor; refresh and logout check the Origin header
// (config/RefreshOriginFilter)
@Tag(name = "Auth")
@RestController
@RequestMapping("/auth")
public class LoginController {

    private static final String COOKIE_DESCRIPTION = "Opaque refresh token: HttpOnly, Secure, SameSite=Strict, Path=/auth";

    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieFactory cookies;

    public LoginController(LoginService loginService, RefreshTokenService refreshTokenService,
                           RefreshCookieFactory cookies) {
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.cookies = cookies;
    }

    @Operation(summary = "Log in: short-lived access token in the body, refresh token in an HttpOnly cookie")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = COOKIE_DESCRIPTION,
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid body",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Invalid e-mail or password",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "429", description = "Too many attempts, per client IP or per e-mail",
                    headers = @Header(name = "Retry-After", description = "Seconds until the next attempt",
                            schema = @Schema(type = "integer")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return withSession(loginService.login(request));
    }

    @Operation(summary = "Trade the refresh cookie for a new access token and a new refresh cookie",
            description = "Rotates: the cookie used is revoked and a new pair is issued. Presenting a refresh token "
                    + "that was already used or revoked revokes every refresh token of that user.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access token and new refresh cookie",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = COOKIE_DESCRIPTION,
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Refresh cookie missing, unknown, expired or already used; "
                    + "the cookie is cleared",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Origin header missing or not in cors.allowed-origins",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "429", description = "Too many attempts from this client IP",
                    headers = @Header(name = "Retry-After", description = "Seconds until the next attempt",
                            schema = @Schema(type = "integer")),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            return withSession(refreshTokenService.rotate(refreshToken));
        } catch (InvalidRefreshTokenException e) {
            // a refresh token that does not work is useless: drop it from the browser too
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear());
            throw e;
        }
    }

    @Operation(summary = "Revoke the refresh token in the cookie and clear the cookie",
            description = "Idempotent: answers 204 even when there is no cookie or it is already revoked.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out; the refresh cookie is cleared",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "Empty refresh_token cookie with Max-Age=0 (same attributes)",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "403", description = "Origin header missing or not in cors.allowed-origins",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear())
                .build();
    }

    private ResponseEntity<LoginResponseDTO> withSession(RefreshTokenService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refreshToken(), session.refreshTtl()))
                .body(new LoginResponseDTO(session.accessToken()));
    }
}
