package pladBack.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import pladBack.entity.Dependent;
import pladBack.entity.QuestionTriage;
import pladBack.entity.QuestionType;
import pladBack.entity.ResultQuestionTriage;
import pladBack.entity.Triage;
import pladBack.entity.User;
import pladBack.entity.sex;
import pladBack.entity.userType;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TriageRepositoryTest {

    @Autowired
    private userRepository userRepository;
    @Autowired
    private dependentRepository dependentRepository;
    @Autowired
    private triageRepository triageRepository;
    @Autowired
    private questionTriageRepository questionTriageRepository;
    @Autowired
    private resultQuestionTriageRepository resultQuestionTriageRepository;

    @Test
    void seededQuestionsComeBackInDisplayOrderWithOptions() {
        List<QuestionTriage> questions = questionTriageRepository.findAllByOrderByDisplayOrderAsc();

        assertThat(questions).isNotEmpty();
        assertThat(questions).isSortedAccordingTo(Comparator.comparingInt(QuestionTriage::getDisplayOrder));

        QuestionTriage first = questions.get(0);
        assertThat(first.getQuestionType()).isEqualTo(QuestionType.single_choice);
        assertThat(first.getOptions()).hasSize(5);
        assertThat(first.getOptions().get(0)).containsEntry("code", "AGE_UNDER_18");

        assertThat(questions).filteredOn(q -> q.getQuestionType() == QuestionType.free_text)
                .isNotEmpty()
                .allSatisfy(q -> assertThat(q.getOptions()).isNull());
    }

    @Test
    void existsByDependentUserIdOnlyMatchesThatDependent() {
        Dependent withTriage = newDependent();
        Dependent withoutTriage = newDependent();
        newTriage(withTriage);

        assertThat(triageRepository.existsByDependentUserId(withTriage.getUserId())).isTrue();
        assertThat(triageRepository.existsByDependentUserId(withoutTriage.getUserId())).isFalse();
    }

    @Test
    void deleteByTriageIdOnlyRemovesThatTriageAndAllowsReinsertingSameValue() {
        QuestionTriage question = questionTriageRepository.findAllByOrderByDisplayOrderAsc().get(0);
        Triage target = newTriage(newDependent());
        Triage other = newTriage(newDependent());
        newResult(target, question, "AGE_18_24");
        newResult(other, question, "AGE_18_24");

        resultQuestionTriageRepository.deleteByTriageId(target.getId());

        assertThat(resultQuestionTriageRepository.findByTriageId(target.getId())).isEmpty();
        assertThat(resultQuestionTriageRepository.findByTriageId(other.getId())).hasSize(1);
        assertThatCode(() -> newResult(target, question, "AGE_18_24")).doesNotThrowAnyException();
    }

    private Dependent newDependent() {
        User user = new User();
        user.setReal_name("triage test");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword_hash("test-hash");
        user.setBirth_date(LocalDate.of(2000, 1, 1));
        user.setGender(sex.outro);
        user.setTypeUser(userType.dependente);
        userRepository.save(user);

        Dependent dependent = new Dependent();
        dependent.setUser(user);
        return dependentRepository.saveAndFlush(dependent);
    }

    private Triage newTriage(Dependent dependent) {
        Triage triage = new Triage();
        triage.setDependent(dependent);
        return triageRepository.saveAndFlush(triage);
    }

    private void newResult(Triage triage, QuestionTriage question, String value) {
        ResultQuestionTriage row = new ResultQuestionTriage();
        row.setTriage(triage);
        row.setQuestion(question);
        row.setValueResult(value);
        resultQuestionTriageRepository.saveAndFlush(row);
    }
}
