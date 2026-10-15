package pladBack.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import pladBack.DTO.AnswerRequestDTO;
import pladBack.DTO.AnswerResponseDTO;
import pladBack.DTO.QuestionResponseDTO;
import pladBack.DTO.TriageRequestDTO;
import pladBack.DTO.TriageResponseDTO;
import pladBack.entity.Dependent;
import pladBack.entity.QuestionTriage;
import pladBack.entity.QuestionType;
import pladBack.entity.ResultQuestionTriage;
import pladBack.entity.Triage;
import pladBack.exception.ForbiddenActionException;
import pladBack.exception.InvalidTriageException;
import pladBack.exception.TriageAlreadyExistException;
import pladBack.exception.TriageNotFoundException;
import pladBack.repositories.dependentRepository;
import pladBack.repositories.questionTriageRepository;
import pladBack.repositories.resultQuestionTriageRepository;
import pladBack.repositories.triageRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TRIAGE_ID = 99L;

    @Mock
    private questionTriageRepository questionTriageRepository;
    @Mock
    private triageRepository triageRepository;
    @Mock
    private resultQuestionTriageRepository resultQuestionTriageRepository;
    @Mock
    private dependentRepository dependentRepository;

    @InjectMocks
    private TriageService triageService;

    @Captor
    private ArgumentCaptor<List<ResultQuestionTriage>> rowsCaptor;

    private final QuestionTriage age = question(1L, QuestionType.single_choice, 1, "AGE_18_24", "AGE_25_39");
    private final QuestionTriage reasons = question(2L, QuestionType.multi_choice, 2, "REDUCE_OR_STOP", "CONCERNED_USE", "OTHER");
    private final QuestionTriage notes = question(10L, QuestionType.free_text, 10);

    // getQuestions

    @Test
    void getQuestionsMapsOptionsAndKeepsRepositoryOrder() {
        when(questionTriageRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(age, notes));

        List<QuestionResponseDTO> questions = triageService.getQuestions();

        assertThat(questions).extracting(QuestionResponseDTO::id).containsExactly(1L, 10L);
        QuestionResponseDTO first = questions.get(0);
        assertThat(first.questionType()).isEqualTo(QuestionType.single_choice);
        assertThat(first.displayOrder()).isEqualTo((short) 1);
        assertThat(first.options()).extracting("code").containsExactly("AGE_18_24", "AGE_25_39");
        assertThat(first.options()).extracting("label").containsExactly("label AGE_18_24", "label AGE_25_39");
        assertThat(questions.get(1).options()).isEmpty();
    }

    // submit: success

    @Test
    void submitSavesEveryValueAndReturnsAnswersInDisplayOrder() {
        givenDependentWithoutTriage();
        givenSaveAssignsId();

        TriageResponseDTO response = triageService.submit(USER_ID, request(
                answer(notes, "  quero apoio  "),
                answer(reasons, "REDUCE_OR_STOP", "OTHER"),
                answer(age, "AGE_18_24")));

        assertThat(response.id()).isEqualTo(TRIAGE_ID);
        assertThat(response.answers()).extracting(AnswerResponseDTO::questionId).containsExactly(1L, 2L, 10L);
        assertThat(response.answers().get(1).values()).containsExactly("REDUCE_OR_STOP", "OTHER");
        assertThat(response.answers().get(2).values()).containsExactly("quero apoio");

        verify(resultQuestionTriageRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).extracting(ResultQuestionTriage::getValueResult)
                .containsExactly("AGE_18_24", "REDUCE_OR_STOP", "OTHER", "quero apoio");
        assertThat(rowsCaptor.getValue()).allSatisfy(row -> assertThat(row.getTriage().getId()).isEqualTo(TRIAGE_ID));
    }

    @Test
    void submitOnlySavesAnsweredQuestions() {
        givenDependentWithoutTriage();
        givenSaveAssignsId();

        TriageResponseDTO response = triageService.submit(USER_ID, request(answer(age, "AGE_25_39")));

        assertThat(response.answers()).hasSize(1);
        verify(resultQuestionTriageRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).hasSize(1);
    }

    @Test
    void submitAcceptsEmptyTriage() {
        givenDependentWithoutTriage();
        givenSaveAssignsId();

        TriageResponseDTO response = triageService.submit(USER_ID, request());

        assertThat(response.id()).isEqualTo(TRIAGE_ID);
        assertThat(response.answers()).isEmpty();
        verify(resultQuestionTriageRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).isEmpty();
    }

    @Test
    void submitAcceptsFreeTextAtMaxLength() {
        givenDependentWithoutTriage();
        givenSaveAssignsId();

        TriageResponseDTO response = triageService.submit(USER_ID, request(answer(notes, "a".repeat(1000))));

        assertThat(response.answers().get(0).values().get(0)).hasSize(1000);
    }

    // submit: rejected

    @Test
    void submitRejectsUserWhoIsNotDependent() {
        when(dependentRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triageService.submit(USER_ID, request(answer(age, "AGE_18_24"))))
                .isInstanceOf(ForbiddenActionException.class);
        verifyNoInteractions(triageRepository, resultQuestionTriageRepository);
    }

    @Test
    void submitRejectsSecondTriage() {
        when(dependentRepository.findById(USER_ID)).thenReturn(Optional.of(dependent()));
        when(triageRepository.existsByDependentUserId(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> triageService.submit(USER_ID, request(answer(age, "AGE_18_24"))))
                .isInstanceOf(TriageAlreadyExistException.class);
        verify(triageRepository, never()).saveAndFlush(any());
        verifyNoInteractions(resultQuestionTriageRepository);
    }

    @Test
    void submitTurnsConcurrentDuplicateIntoConflict() {
        givenDependentWithoutTriage();
        when(triageRepository.saveAndFlush(any(Triage.class))).thenThrow(new DataIntegrityViolationException("dependent_id"));

        assertThatThrownBy(() -> triageService.submit(USER_ID, request(answer(age, "AGE_18_24"))))
                .isInstanceOf(TriageAlreadyExistException.class);
        verifyNoInteractions(resultQuestionTriageRepository);
    }

    @Test
    void submitRejectsUnknownQuestion() {
        assertSubmitInvalid(new AnswerRequestDTO(999L, List.of("X")));
    }

    @Test
    void submitRejectsSameQuestionTwice() {
        assertSubmitInvalid(answer(age, "AGE_18_24"), answer(age, "AGE_25_39"));
    }

    @Test
    void submitRejectsSingleChoiceWithTwoValues() {
        assertSubmitInvalid(answer(age, "AGE_18_24", "AGE_25_39"));
    }

    @Test
    void submitRejectsSingleChoiceWithUnknownCode() {
        assertSubmitInvalid(answer(age, "AGE_99"));
    }

    @Test
    void submitRejectsCodeWithWrongCase() {
        assertSubmitInvalid(answer(age, "age_18_24"));
    }

    @Test
    void submitRejectsMultiChoiceWithRepeatedCode() {
        assertSubmitInvalid(answer(reasons, "OTHER", "OTHER"));
    }

    @Test
    void submitRejectsMultiChoiceWithUnknownCode() {
        assertSubmitInvalid(answer(reasons, "OTHER", "NOT_AN_OPTION"));
    }

    @Test
    void submitRejectsFreeTextWithTwoValues() {
        assertSubmitInvalid(answer(notes, "a", "b"));
    }

    @Test
    void submitRejectsBlankFreeText() {
        assertSubmitInvalid(answer(notes, "   "));
    }

    @Test
    void submitRejectsFreeTextOverMaxLength() {
        assertSubmitInvalid(answer(notes, "a".repeat(1001)));
    }

    // resubmit

    @Test
    void resubmitReplacesAnswersOfExistingTriage() {
        when(dependentRepository.findById(USER_ID)).thenReturn(Optional.of(dependent()));
        when(triageRepository.findByDependentUserId(USER_ID)).thenReturn(Optional.of(existingTriage()));
        when(questionTriageRepository.findAll()).thenReturn(List.of(age, reasons, notes));

        TriageResponseDTO response = triageService.resubmit(USER_ID, request(answer(age, "AGE_25_39")));

        assertThat(response.id()).isEqualTo(TRIAGE_ID);
        assertThat(response.answers()).extracting(AnswerResponseDTO::values).containsExactly(List.of("AGE_25_39"));
        InOrder order = inOrder(resultQuestionTriageRepository);
        order.verify(resultQuestionTriageRepository).deleteByTriageId(TRIAGE_ID);
        order.verify(resultQuestionTriageRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).extracting(ResultQuestionTriage::getValueResult).containsExactly("AGE_25_39");
    }

    @Test
    void resubmitWithEmptyAnswersClearsTriage() {
        when(dependentRepository.findById(USER_ID)).thenReturn(Optional.of(dependent()));
        when(triageRepository.findByDependentUserId(USER_ID)).thenReturn(Optional.of(existingTriage()));
        when(questionTriageRepository.findAll()).thenReturn(List.of(age, reasons, notes));

        TriageResponseDTO response = triageService.resubmit(USER_ID, request());

        assertThat(response.answers()).isEmpty();
        verify(resultQuestionTriageRepository).deleteByTriageId(TRIAGE_ID);
        verify(resultQuestionTriageRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).isEmpty();
    }

    @Test
    void resubmitRequiresExistingTriage() {
        when(dependentRepository.findById(USER_ID)).thenReturn(Optional.of(dependent()));
        when(triageRepository.findByDependentUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triageService.resubmit(USER_ID, request(answer(age, "AGE_18_24"))))
                .isInstanceOf(TriageNotFoundException.class);
        verifyNoInteractions(resultQuestionTriageRepository);
    }

    @Test
    void resubmitRejectsUserWhoIsNotDependent() {
        when(dependentRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triageService.resubmit(USER_ID, request()))
                .isInstanceOf(ForbiddenActionException.class);
        verifyNoInteractions(triageRepository, resultQuestionTriageRepository);
    }

    @Test
    void resubmitWithInvalidAnswerKeepsPreviousAnswers() {
        when(dependentRepository.findById(USER_ID)).thenReturn(Optional.of(dependent()));
        when(triageRepository.findByDependentUserId(USER_ID)).thenReturn(Optional.of(existingTriage()));
        when(questionTriageRepository.findAll()).thenReturn(List.of(age, reasons, notes));

        assertThatThrownBy(() -> triageService.resubmit(USER_ID, request(answer(age, "AGE_99"))))
                .isInstanceOf(InvalidTriageException.class);
        verify(resultQuestionTriageRepository, never()).deleteByTriageId(anyLong());
        verify(resultQuestionTriageRepository, never()).saveAll(any());
    }

    // helpers

    private void assertSubmitInvalid(AnswerRequestDTO... answers) {
        givenDependentWithoutTriage();

        assertThatThrownBy(() -> triageService.submit(USER_ID, request(answers)))
                .isInstanceOf(InvalidTriageException.class);
        verify(triageRepository, never()).saveAndFlush(any());
        verifyNoInteractions(resultQuestionTriageRepository);
    }

    private void givenDependentWithoutTriage() {
        when(dependentRepository.findById(USER_ID)).thenReturn(Optional.of(dependent()));
        when(triageRepository.existsByDependentUserId(USER_ID)).thenReturn(false);
        when(questionTriageRepository.findAll()).thenReturn(List.of(age, reasons, notes));
    }

    private void givenSaveAssignsId() {
        when(triageRepository.saveAndFlush(any(Triage.class))).thenAnswer(invocation -> {
            Triage triage = invocation.getArgument(0);
            triage.setId(TRIAGE_ID);
            return triage;
        });
    }

    private static Dependent dependent() {
        Dependent dependent = new Dependent();
        dependent.setUserId(USER_ID);
        return dependent;
    }

    private static Triage existingTriage() {
        Triage triage = new Triage();
        triage.setId(TRIAGE_ID);
        triage.setDependent(dependent());
        return triage;
    }

    private static QuestionTriage question(Long id, QuestionType type, int displayOrder, String... codes) {
        QuestionTriage question = new QuestionTriage();
        question.setId(id);
        question.setQuestionText("question " + id);
        question.setQuestionType(type);
        question.setDisplayOrder((short) displayOrder);
        question.setOptions(codes.length == 0 ? null : Arrays.stream(codes)
                .map(code -> Map.of("code", code, "label", "label " + code))
                .toList());
        return question;
    }

    private static AnswerRequestDTO answer(QuestionTriage question, String... values) {
        return new AnswerRequestDTO(question.getId(), List.of(values));
    }

    private static TriageRequestDTO request(AnswerRequestDTO... answers) {
        return new TriageRequestDTO(List.of(answers));
    }
}
