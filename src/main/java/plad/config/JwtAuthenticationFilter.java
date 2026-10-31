package plad.config;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
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
import plad.services.JwtService;

// not a @Component: Spring Boot would also register it as a plain servlet filter outside the security chain
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            authenticate(authHeader.substring(BEARER_PREFIX.length()));
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
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + claims.role().name().toUpperCase(Locale.ROOT)));
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(claims.userId(), null, authorities));
        } catch (IllegalArgumentException e) {
            log.debug("Ignoring invalid JWT: {}", e.getMessage());
        }
    }
}
