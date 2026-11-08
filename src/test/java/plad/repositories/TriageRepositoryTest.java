package plad.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import plad.config.TestcontainersConfig;
import plad.entity.CrmCrpType;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.QuestionTriage;
import plad.entity.QuestionType;
import plad.entity.ResultQuestionTriage;
import plad.entity.Triage;
import plad.entity.User;
import plad.entity.Sex;
import plad.entity.UserType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    @Autowired
    private PsychologistRepository psychologistRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    // ---- what a psychologist may read: registered in a non-cancelled meeting of theirs -------------------------------

    @Test
    void visibleToPsychologistMatchesOnlyRegisteredDependentsInNonCancelledMeetingsOfThatPsychologist() {
        User psychologist = newPsychologist();
        User other = newPsychologist();
        Dependent inScheduled = newDependent();
        Dependent inEndedAndTwoScheduled = newDependent();
        Dependent onlyInCancelled = newDependent();
        Dependent inAnothersMeeting = newDependent();
        Dependent inNoMeeting = newDependent();
        Triage visibleOne = newTriage(inScheduled);
        Triage visibleTwo = newTriage(inEndedAndTwoScheduled);
        Triage cancelledOnly = newTriage(onlyInCancelled);
        Triage anothers = newTriage(inAnothersMeeting);
        Triage nobodys = newTriage(inNoMeeting);

        register(newMeeting(psychologist, 0, "scheduled"), inScheduled);
        register(newMeeting(psychologist, 1, "ended"), inEndedAndTwoScheduled);
        register(newMeeting(psychologist, 2, "scheduled"), inEndedAndTwoScheduled);
        register(newMeeting(psychologist, 3, "scheduled"), inEndedAndTwoScheduled);
        register(newMeeting(psychologist, 4, "cancelled"), onlyInCancelled);
        register(newMeeting(other, 0, "scheduled"), inAnothersMeeting);

        Page<Triage> page = triageRepository.findVisibleToPsychologist(psychologist.getId(),
                PageRequest.of(0, 50, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        // the dependent in three meetings comes back once, and the page count agrees
        assertThat(page.getContent()).extracting(Triage::getId).containsExactly(visibleTwo.getId(), visibleOne.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);

        assertThat(triageRepository.findVisibleToPsychologistById(psychologist.getId(), visibleOne.getId())).isPresent();
        assertThat(triageRepository.findVisibleToPsychologistById(psychologist.getId(), visibleTwo.getId())).isPresent();
        for (Triage outside : List.of(cancelledOnly, anothers, nobodys)) {
            assertThat(triageRepository.findVisibleToPsychologistById(psychologist.getId(), outside.getId()))
                    .as("triage %d", outside.getId()).isEmpty();
        }
        // the other psychologist reads their own and nothing of the first one's
        assertThat(triageRepository.findVisibleToPsychologistById(other.getId(), anothers.getId())).isPresent();
        assertThat(triageRepository.findVisibleToPsychologistById(other.getId(), visibleOne.getId())).isEmpty();
        assertThat(triageRepository.findVisibleToPsychologist(other.getId(), PageRequest.of(0, 50)).getContent())
                .extracting(Triage::getId).containsExactly(anothers.getId());
    }

    @Test
    void visibleToPsychologistPagesNewestFirstAndCountsOnlyWhatIsVisible() {
        User psychologist = newPsychologist();
        List<Triage> visible = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Dependent dependent = newDependent();
            visible.add(newTriage(dependent));
            register(newMeeting(psychologist, i, "scheduled"), dependent);
            newTriage(newDependent()); // not in any meeting: never counted
        }
        Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        Page<Triage> first = triageRepository.findVisibleToPsychologist(psychologist.getId(), PageRequest.of(0, 2, newestFirst));
        Page<Triage> second = triageRepository.findVisibleToPsychologist(psychologist.getId(), PageRequest.of(1, 2, newestFirst));

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(Triage::getId).containsExactly(visible.get(2).getId(), visible.get(1).getId());
        assertThat(second.getContent()).extracting(Triage::getId).containsExactly(visible.get(0).getId());
    }

    @Test
    void visibleToPsychologistWithNoMeetingIsEmpty() {
        newTriage(newDependent());

        Page<Triage> page = triageRepository.findVisibleToPsychologist(newPsychologist().getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    private User newPsychologist() {
        User user = new User();
        user.setRealName("psychologist test");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(1985, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(UserType.psicologo);
        userRepository.save(user);

        Psychologist psychologist = new Psychologist();
        psychologist.setUser(user);
        psychologist.setCredentialType(CrmCrpType.CRP);
        psychologist.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 20));
        psychologistRepository.saveAndFlush(psychologist);
        return user;
    }

    // `offsetHours` spaces the scheduled meetings of one psychologist apart, so meetings_no_overlap never trips
    private long newMeeting(User psychologist, int offsetHours, String status) {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS).plus(2L * offsetHours, ChronoUnit.HOURS);
        return jdbcTemplate.queryForObject("""
                        insert into meetings (psychologist_id, title, starts_at, ends_at, capacity, status)
                        values (?, 'Sala', ?, ?, 10, ?) returning id""",
                Long.class, psychologist.getId(), OffsetDateTime.ofInstant(start, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(start.plus(1, ChronoUnit.HOURS), ZoneOffset.UTC), status);
    }

    private void register(long meetingId, Dependent dependent) {
        jdbcTemplate.update("insert into meeting_registrations (meeting_id, dependent_id) values (?, ?)",
                meetingId, dependent.getUserId());
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
        dependent.setNickname(plad.TestNicknames.unique());
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
