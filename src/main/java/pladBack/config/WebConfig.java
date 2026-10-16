package pladBack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pladBack.services.RateLimiterService;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiterService rateLimiterService;

    public WebConfig(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimiterService))
                .addPathPatterns("/auth/login", "/auth/register");
    }
}
