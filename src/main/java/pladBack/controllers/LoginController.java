package pladBack.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pladBack.DTO.LoginRequestDTO;
import pladBack.DTO.LoginResponseDTO;
import pladBack.exception.TooManyRequestException;
import pladBack.services.LoginService;
import pladBack.services.RateLimiterService;

@RestController
@RequestMapping("/auth")
public class LoginController {

    private final LoginService loginService;
    private final RateLimiterService rateLimiterService;

    public LoginController(LoginService loginService, RateLimiterService rateLimiterService) {
        this.loginService = loginService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {

        String ip = httpRequest.getRemoteAddr();

        if(!rateLimiterService.tryConsume(ip)) {
            throw new TooManyRequestException("Too Many Requests. Try again in a minute.");
        }

        LoginResponseDTO response = loginService.login(request);
        return ResponseEntity.ok(response);
    }
}