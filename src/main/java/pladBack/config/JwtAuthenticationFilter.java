package pladBack.config;

import io.jsonwebtoken.Claims;
import java.io.IOException;
import java.util.Collections;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pladBack.services.JwtService;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @java.lang.Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authReader = request.getHeader("Authorization");

        if(authReader == null || !authReader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authReader.substring("Bearer ".length());

        try {
            Claims claims = jwtService.validateAndExtractClaims(token);
            Long userId = claims.get("userId", Long.class);

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());

            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (IllegalArgumentException e) {
        }
        filterChain.doFilter(request, response);

    }
}
