package plad.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import plad.services.JwtService;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final List<String> allowedOrigins;
    private final boolean apiDocsEnabled;

    public SecurityConfig(
            JwtService jwtService,
            @Value("${cors.allowed-origins}") String allowedOrigins,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled
    ) {
        this.jwtService = jwtService;
        this.allowedOrigins = parseOrigins(allowedOrigins);
        this.apiDocsEnabled = apiDocsEnabled;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // explicit source: HandlerMappingIntrospector is also a CorsConfigurationSource
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // listed one by one so a future /auth endpoint is not public by accident
                    auth.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register").permitAll();
                    // the error dispatch must stay reachable or anonymous errors turn into a bare 401
                    auth.requestMatchers("/error").permitAll();
                    // public only while springdoc.api-docs.enabled=true; otherwise they fall under authenticated()
                    if (apiDocsEnabled) {
                        auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                // missing or invalid token: 401 so the client knows to log in again (403 stays "not allowed")
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

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
