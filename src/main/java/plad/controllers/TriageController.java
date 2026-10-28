package plad.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plad.dto.QuestionResponseDTO;
import plad.dto.TriageRequestDTO;
import plad.dto.TriageResponseDTO;
import plad.services.TriageService;

import java.util.List;

@RestController
@RequestMapping("/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @GetMapping("/questions")
    public ResponseEntity<List<QuestionResponseDTO>> getQuestions() {
        return ResponseEntity.ok(triageService.getQuestions());
    }

    @PostMapping
    public ResponseEntity<TriageResponseDTO> submit(@AuthenticationPrincipal Long userId,
                                                    @Valid @RequestBody TriageRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(triageService.submit(userId, request));
    }

    @PutMapping
    public ResponseEntity<TriageResponseDTO> resubmit(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody TriageRequestDTO request) {
        return ResponseEntity.ok(triageService.resubmit(userId, request));
    }
}
