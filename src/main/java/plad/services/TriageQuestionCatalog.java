package plad.services;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import plad.config.CacheConfig;
import plad.entity.QuestionTriage;
import plad.repositories.QuestionTriageRepository;

import java.util.List;

// The triage questions are seeded by migration and never change at runtime, so they are read once per JVM.
// A separate bean because @Cacheable works through the Spring proxy and would be skipped on a call from inside
// TriageService itself.
@Service
public class TriageQuestionCatalog {

    private final QuestionTriageRepository questionTriageRepository;

    public TriageQuestionCatalog(QuestionTriageRepository questionTriageRepository) {
        this.questionTriageRepository = questionTriageRepository;
    }

    // ordered by display_order; the entities are detached and must be treated as read-only
    @Cacheable(CacheConfig.TRIAGE_QUESTIONS)
    public List<QuestionTriage> findAllOrdered() {
        return List.copyOf(questionTriageRepository.findAllByOrderByDisplayOrderAsc());
    }
}
