package plad.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import plad.config.CacheConfig;
import plad.entity.QuestionTriage;
import plad.repositories.QuestionTriageRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Tiny context (cache + catalog + a mocked repository): no database and no Hikari pool, so it adds no connections
// to the shared Testcontainers Postgres.
@SpringJUnitConfig
class TriageQuestionCatalogTest {

    @Configuration
    @Import({CacheConfig.class, TriageQuestionCatalog.class})
    static class Config {
        @Bean
        QuestionTriageRepository questionTriageRepository() {
            return mock(QuestionTriageRepository.class);
        }
    }

    @Autowired
    private TriageQuestionCatalog catalog;
    @Autowired
    private QuestionTriageRepository repository;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void freshCache() {
        cacheManager.getCache(CacheConfig.TRIAGE_QUESTIONS).clear();
        reset(repository);
    }

    @Test
    void secondCallComesFromTheCacheAndDoesNotQueryTheRepository() {
        QuestionTriage question = new QuestionTriage();
        question.setCode("AGE_RANGE");
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(question));

        List<QuestionTriage> first = catalog.findAllOrdered();
        List<QuestionTriage> second = catalog.findAllOrdered();

        assertThat(first).containsExactly(question);
        assertThat(second).isSameAs(first);
        verify(repository, times(1)).findAllByOrderByDisplayOrderAsc();
    }
}
