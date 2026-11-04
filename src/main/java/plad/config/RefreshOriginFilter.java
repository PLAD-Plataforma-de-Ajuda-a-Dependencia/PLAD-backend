package plad.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

// The refresh cookie is sent by the browser on its own, so /auth/refresh and /auth/logout accept a request only
// from a page served by one of cors.allowed-origins. A missing Origin is refused too: browsers always send it on a
// POST, so only a non-browser client lacks it, and those have no cookie to refresh with.
// A filter in the security chain (before CorsFilter), not an MVC interceptor, so every refusal is the same JSON 403
// instead of Spring's plain-text CORS answer for a foreign origin. Preflights (OPTIONS) are left to CorsFilter.
public class RefreshOriginFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RefreshOriginFilter.class);
    static final Set<String> PROTECTED_PATHS = Set.of("/auth/refresh", "/auth/logout");

    private final List<String> allowedOrigins;
    private final SecurityErrorHandler securityErrorHandler;

    public RefreshOriginFilter(List<String> allowedOrigins, SecurityErrorHandler securityErrorHandler) {
        this.allowedOrigins = allowedOrigins.stream().map(RefreshOriginFilter::withoutTrailingSlash).toList();
        this.securityErrorHandler = securityErrorHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !allowedOrigins.contains(withoutTrailingSlash(origin.trim()))) {
            log.debug("Refusing {} from origin {}", request.getRequestURI(), origin);
            securityErrorHandler.handle(request, response, new AccessDeniedException("Origin not allowed"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String withoutTrailingSlash(String origin) {
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }
}
