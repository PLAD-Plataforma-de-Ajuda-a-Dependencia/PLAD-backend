package plad.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import plad.config.TestcontainersConfig;
import plad.entity.Dependent;
import plad.entity.QuestionTriage;
import plad.entity.QuestionType;
import plad.entity.ResultQuestionTriage;
import plad.entity.Triage;
import plad.entity.User;
import plad.entity.Sex;
import plad.entity.UserType;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
class TriageRepositoryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DependentRepository dependentRepository;
    @Autowired
    private TriageRepository triageRepository;
    @Autowired
    private QuestionTriageRepository questionTriageRepository;
    @Autowired
    private ResultQuestionTriageRepository resultQuestionTriageRepository;

    @Test
    void seededQuestionsComeBackInDisplayOrderWithOptions() {
        List<QuestionTriage> questions = questionTriageRepository.findAllByOrderByDisplayOrderAsc();

        assertThat(questions).isNotEmpty();
        assertThat(questions).isSortedAccordingTo(Comparator.comparingInt(QuestionTriage::getDisplayOrder));

        QuestionTriage first = questions.get(0);
        assertThat(first.getQuestionType()).isEqualTo(QuestionType.single_choice);
        // V12 removed AGE_UNDER_18 (adults only) and kept the order of the rest
        assertThat(first.getOptions()).extracting(option -> option.get("code"))
                .containsExactly("AGE_18_24", "AGE_25_39", "AGE_40_59", "AGE_60_PLUS");

        assertThat(questions).filteredOn(q -> q.getQuestionType() == QuestionType.free_text)
                .isNotEmpty()
                .allSatisfy(q -> assertThat(q.getOptions()).isNull());
    }

    @Test
    void seededQuestionsHaveStableUniqueCodes() {
        List<QuestionTriage> questions = questionTriageRepository.findAllByOrderByDisplayOrderAsc();

        assertThat(questions).extracting(QuestionTriage::getCode).containsExactly(
                "AGE_RANGE", "REASON", "USE_FREQUENCY_30D", "CONTROL_DIFFICULTY", "SUPPORT_NETWORK",
                "PREVIOUS_TREATMENT", "GROUP_EXPERIENCE", "PREFERRED_FORMAT", "FOLLOWUP_STYLE", "NOTES_FOR_TEAM");
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

    @Test
    void freeTextWith1000EmojisIsSaved() {
        QuestionTriage question = questionTriageRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(q -> q.getQuestionType() == QuestionType.free_text)
                .findFirst().orElseThrow();
        Triage triage = newTriage(newDependent());
        // varied emojis (4 bytes each, ~4000 bytes) so the value can't be compressed below the btree entry limit
        Random random = new Random(42);
        String emojis = random.ints(1000, 0x1F300, 0x1F650)
                .mapToObj(Character::toString)
                .collect(Collectors.joining());

        assertThatCode(() -> newResult(triage, question, emojis)).doesNotThrowAnyException();

        assertThat(resultQuestionTriageRepository.findByTriageId(triage.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getValueResult()).isEqualTo(emojis));
    }

    @Test
    void sameValueTwiceForTheSameQuestionIsStillRejected() {
        QuestionTriage question = questionTriageRepository.findAllByOrderByDisplayOrderAsc().get(0);
        Triage triage = newTriage(newDependent());
        newResult(triage, question, "AGE_18_24");

        assertThatThrownBy(() -> newResult(triage, question, "AGE_18_24"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Dependent newDependent() {
        User user = new User();
        user.setRealName("triage test");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(UserType.dependente);
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
