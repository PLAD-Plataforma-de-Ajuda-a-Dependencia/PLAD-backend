package pladBack.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pladBack.DTO.QuestionResponseDTO;
import pladBack.DTO.TriageRequestDTO;
import pladBack.DTO.TriageResponseDTO;
import pladBack.entity.Triage;
import pladBack.repositories.dependentRepository;
import pladBack.repositories.questionTriageRepository;
import pladBack.repositories.resultQuestionTriageRepository;
import pladBack.repositories.triageRepository;

import java.util.List;

@Service
public class TriageService {

    private static final int MAX_FREE_TEXT_LENGTH = 1000;

    private final questionTriageRepository questionTriageRepository;
    private final triageRepository triageRepository;
    private final resultQuestionTriageRepository resultQuestionTriageRepository;
    private final dependentRepository dependentRepository;

    //constructor injection
    public TriageService(questionTriageRepository questionTriageRepository, triageRepository triageRepository, resultQuestionTriageRepository resultQuestionTriageRepository, dependentRepository dependentRepository) {
        this.questionTriageRepository = questionTriageRepository;
        this.triageRepository = triageRepository;
        this.resultQuestionTriageRepository = resultQuestionTriageRepository;
        this.dependentRepository = dependentRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionResponseDTO> getQuestions() {
        //
    }

    @Transactional
    public TriageResponseDTO submit(Long userId, TriageRequestDTO request) {

        
    }
}
