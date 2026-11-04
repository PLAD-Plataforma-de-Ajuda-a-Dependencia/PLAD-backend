package plad.config;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import plad.repositories.UserRepository;
import plad.services.JwtService;

// not a @Component: Spring Boot would also register it as a plain servlet filter outside the security chain
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityErrorHandler securityErrorHandler;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   SecurityErrorHandler securityErrorHandler) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.securityErrorHandler = securityErrorHandler;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            try {
                authenticate(authHeader.substring(BEARER_PREFIX.length()));
            } catch (TokenVersionLookupException e) {
                log.warn("Could not check the token version ({})", e.getCause().getClass().getSimpleName());
                securityErrorHandler.serviceUnavailable(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    // invalid tokens are ignored: the request goes on unauthenticated and protected routes answer 401
    private void authenticate(String token) {
        try {
            JwtService.TokenClaims claims = jwtService.parse(token);
            if (claims.userId() == null) {
                log.debug("Ignoring JWT without a userId claim");
                return;
            }
            // tokens issued before the role claim existed are invalid: the user logs in again
            if (claims.role() == null) {
                log.debug("Ignoring JWT without a role claim");
                return;
            }
            // same for the version claim; a version that is not the current one means the sessions were revoked
            if (claims.tokenVersion() == null || !claims.tokenVersion().equals(currentTokenVersion(claims.userId()).orElse(null))) {
                log.debug("Ignoring JWT with a stale or missing token version");
                return;
            }
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + claims.role().name().toUpperCase(Locale.ROOT)));
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(claims.userId(), null, authorities));
        } catch (IllegalArgumentException e) {
            log.debug("Ignoring invalid JWT: {}", e.getMessage());
        }
    }

    // one indexed single-column read per authenticated request; empty when the user no longer exists
    private Optional<Integer> currentTokenVersion(Long userId) {
        try {
            return userRepository.findTokenVersionById(userId);
        } catch (RuntimeException e) {
            throw new TokenVersionLookupException(e);
        }
    }

    private static final class TokenVersionLookupException extends RuntimeException {
        TokenVersionLookupException(Throwable cause) {
            super(cause);
        }
    }
}
