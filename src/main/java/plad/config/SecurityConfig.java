package plad.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // login and the password change set the refresh cookie, refresh rotates it, logout clears it: a cross-origin fetch
    // only stores or sends the cookie when the response allows credentials
    private static final List<String> CREDENTIALED_PATHS =
            List.of("/auth/login", "/auth/refresh", "/auth/logout", "/me/password");

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityErrorHandler securityErrorHandler;
    private final List<String> allowedOrigins;
    private final boolean apiDocsEnabled;

    public SecurityConfig(
            JwtService jwtService,
            UserRepository userRepository,
            SecurityErrorHandler securityErrorHandler,
            @Value("${cors.allowed-origins}") String allowedOrigins,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.securityErrorHandler = securityErrorHandler;
        this.allowedOrigins = parseOrigins(allowedOrigins);
        this.apiDocsEnabled = apiDocsEnabled;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // credentials (cookies) only where the refresh cookie is set or sent; the first match wins, so these go first
        for (String path : CREDENTIALED_PATHS) {
            source.registerCorsConfiguration(path, corsConfiguration(true));
        }
        source.registerCorsConfiguration("/**", corsConfiguration(false));
        return source;
    }

    private CorsConfiguration corsConfiguration(boolean allowCredentials) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(allowCredentials);
        return config;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // explicit source: HandlerMappingIntrospector is also a CorsConfigurationSource
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // listed one by one so a future /auth endpoint is not public by accident;
                    // refresh and logout authenticate with the refresh cookie, not a bearer token
                    auth.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register",
                            "/auth/refresh", "/auth/logout").permitAll();
                    // the error dispatch must stay reachable or anonymous errors turn into a bare 401
                    auth.requestMatchers("/error").permitAll();
                    // orchestrator probes only; /actuator/health itself and any other actuator path need a token
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health/liveness", "/actuator/health/readiness").permitAll();
                    auth.requestMatchers("/admin/**").hasRole("ADMIN");
                    // public only while springdoc.api-docs.enabled=true; otherwise they fall under authenticated()
                    if (apiDocsEnabled) {
                        auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                // missing or invalid token: 401 so the client knows to log in again (403 stays "not allowed");
                // both answer with the same ErrorResponseDTO JSON as GlobalExceptionHandler
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .addFilterBefore(new RefreshOriginFilter(allowedOrigins, securityErrorHandler), CorsFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository, securityErrorHandler),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static List<String> parseOrigins(String raw) {
        List<String> origins = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("cors.allowed-origins must list explicit origins, wildcards are not allowed");
        }
        return origins;
    }
}
