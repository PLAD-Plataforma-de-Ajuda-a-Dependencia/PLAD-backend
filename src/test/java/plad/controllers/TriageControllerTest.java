package plad.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.CrmCrpType;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.QuestionTriage;
import plad.entity.QuestionType;
import plad.entity.User;
import plad.entity.Sex;
import plad.entity.UserType;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.QuestionTriageRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class TriageControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DependentRepository dependentRepository;
    @Autowired
    private PsychologistRepository psychologistRepository;
    @Autowired
    private QuestionTriageRepository questionTriageRepository;

    private List<QuestionTriage> questions;
    private Long singleChoiceId;
    private Long multiChoiceId;
    private Long freeTextId;

    @BeforeEach
    void loadSeededQuestions() {
        questions = questionTriageRepository.findAllByOrderByDisplayOrderAsc();
        singleChoiceId = firstOfType(questions, QuestionType.single_choice);
        multiChoiceId = firstOfType(questions, QuestionType.multi_choice);
        freeTextId = firstOfType(questions, QuestionType.free_text);
    }

    // GET /triage/questions

    @Test
    void getQuestionsReturnsSeededQuestionsInSnakeCase() throws Exception {
        mockMvc.perform(get("/triage/questions").header("Authorization", bearer(newDependentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(singleChoiceId))
                .andExpect(jsonPath("$[0].code").value("AGE_RANGE"))
                .andExpect(jsonPath("$[1].code").value("REASON"))
                .andExpect(jsonPath("$[9].code").value("NOTES_FOR_TEAM"))
                .andExpect(jsonPath("$[0].question_text").isNotEmpty())
                .andExpect(jsonPath("$[0].question_type").value("single_choice"))
                .andExpect(jsonPath("$[0].display_order").value(1))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[0].options[0].code").value("AGE_UNDER_18"))
                .andExpect(jsonPath("$[0].options[0].label").isNotEmpty())
                .andExpect(jsonPath("$[9].question_type").value("free_text"))
                .andExpect(jsonPath("$[9].required").value(false));
    }

    @Test
    void getQuestionsWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/triage/questions"))
                .andExpect(status().isUnauthorized());
    }

    // POST /triage

    @Test
    void submitCreatesTriageAndSecondSubmitIsConflict() throws Exception {
        String token = newDependentToken();
        String body = body(Map.of(
                multiChoiceId, "\"REDUCE_OR_STOP\", \"OTHER\"",
                singleChoiceId, "\"AGE_18_24\"",
                freeTextId, "\"  quero apoio  \""), null);

        submit(token, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.answers.length()").value(10))
                .andExpect(jsonPath("$.answers[0].question_id").value(singleChoiceId))
                .andExpect(jsonPath("$.answers[0].question_code").value("AGE_RANGE"))
                .andExpect(jsonPath("$.answers[1].values.length()").value(2))
                .andExpect(jsonPath("$.answers[9].values[0]").value("quero apoio"));

        submit(token, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void submitIdentifyingQuestionsOnlyByCodeIsCreated() throws Exception {
        submit(newDependentToken(), bodyByCode(Map.of(
                "AGE_RANGE", "\"AGE_25_39\"",
                "REASON", "\"REDUCE_OR_STOP\", \"OTHER\"",
                "NOTES_FOR_TEAM", "\"quero apoio\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.length()").value(10))
                .andExpect(jsonPath("$.answers[0].question_id").value(singleChoiceId))
                .andExpect(jsonPath("$.answers[0].question_code").value("AGE_RANGE"))
                .andExpect(jsonPath("$.answers[0].values[0]").value("AGE_25_39"))
                .andExpect(jsonPath("$.answers[1].values.length()").value(2))
                .andExpect(jsonPath("$.answers[9].question_code").value("NOTES_FOR_TEAM"));
    }

    @Test
    void submitIdentifyingQuestionsOnlyByIdIsCreated() throws Exception {
        submit(newDependentToken(), body())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers[0].question_id").value(singleChoiceId))
                .andExpect(jsonPath("$.answers[0].question_code").value("AGE_RANGE"));
    }

    @Test
    void submitMixingIdAndCodeAcrossAnswersIsCreated() throws Exception {
        String rest = questions.stream()
                .filter(q -> q.isRequired() && !q.getCode().equals("AGE_RANGE"))
                .map(q -> "{\"question_code\": \"%s\", \"values\": [\"%s\"]}".formatted(q.getCode(), q.getOptions().get(0).get("code")))
                .collect(Collectors.joining(","));
        String body = "{\"answers\": [{\"question_id\": %d, \"values\": [\"AGE_18_24\"]},%s]}".formatted(singleChoiceId, rest);

        submit(newDependentToken(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.length()").value(requiredCount()));
    }

    @Test
    void submitWithMatchingIdAndCodeIsCreated() throws Exception {
        String body = body().replace("{\"question_id\": %d,".formatted(singleChoiceId),
                "{\"question_id\": %d, \"question_code\": \"AGE_RANGE\",".formatted(singleChoiceId));

        submit(newDependentToken(), body)
                .andExpect(status().isCreated());
    }

    @Test
    void submitWithIdAndCodeOfDifferentQuestionsIsBadRequestAndDoesNotUseTheSingleSubmission() throws Exception {
        String token = newDependentToken();
        String body = "{\"answers\": [{\"question_id\": %d, \"question_code\": \"REASON\", \"values\": [\"AGE_18_24\"]}]}"
                .formatted(singleChoiceId);

        submit(token, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not match")));

        submit(token, body())
                .andExpect(status().isCreated());
    }

    @Test
    void submitWithUnknownQuestionCodeIsBadRequest() throws Exception {
        submit(newDependentToken(), "{\"answers\": [{\"question_code\": \"NOT_A_QUESTION\", \"values\": [\"X\"]}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("NOT_A_QUESTION")));
    }

    @Test
    void submitAnsweringTheSameQuestionByIdAndByCodeIsBadRequest() throws Exception {
        String body = "{\"answers\": [{\"question_id\": %d, \"values\": [\"AGE_18_24\"]},{\"question_code\": \"AGE_RANGE\", \"values\": [\"AGE_25_39\"]}]}"
                .formatted(singleChoiceId);

        submit(newDependentToken(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Duplicate answer")));
    }

    @Test
    void submitWithOnlyRequiredQuestionsIsCreated() throws Exception {
        submit(newDependentToken(), body())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.length()").value(requiredCount()));
    }

    @Test
    void submitWithEmptyAnswersIsBadRequestAndDoesNotUseTheSingleSubmission() throws Exception {
        String token = newDependentToken();

        submit(token, "{\"answers\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("answers")));

        submit(token, body())
                .andExpect(status().isCreated());
    }

    @Test
    void submitWithMissingRequiredQuestionIsBadRequestAndDoesNotUseTheSingleSubmission() throws Exception {
        String token = newDependentToken();

        submit(token, bodyWithout(multiChoiceId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Missing required questions")))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(multiChoiceId))));

        submit(token, body())
                .andExpect(status().isCreated());
    }

    @Test
    void submitWithTooManyAnswersIsBadRequest() throws Exception {
        String answer = "{\"question_id\": %d, \"values\": [\"AGE_18_24\"]}".formatted(singleChoiceId);
        String body = "{\"answers\": [" + String.join(",", Collections.nCopies(51, answer)) + "]}";

        submit(newDependentToken(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("answers")));
    }

    @Test
    void submitWithTooManyValuesIsBadRequest() throws Exception {
        String values = String.join(",", Collections.nCopies(21, "\"OTHER\""));

        submit(newDependentToken(), answerBody(multiChoiceId, values))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("answers[0].values")));
    }

    @Test
    void submitAsPsychologistIsForbidden() throws Exception {
        submit(newPsychologistToken(), body())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void submitWithInvalidOptionIsBadRequest() throws Exception {
        submit(newDependentToken(), answerBody(singleChoiceId, "\"NOT_AN_OPTION\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("NOT_AN_OPTION")));
    }

    @Test
    void submitWithoutAnswersFieldIsBadRequest() throws Exception {
        submit(newDependentToken(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("answers")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void submitWithoutQuestionIdAndCodeIsBadRequest() throws Exception {
        submit(newDependentToken(), "{\"answers\": [{\"values\": [\"AGE_18_24\"]}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("question_id or question_code")));
    }

    @Test
    void submitWithEmptyValuesIsBadRequest() throws Exception {
        submit(newDependentToken(), answerBody(singleChoiceId, ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("answers[0].values")));
    }

    // PUT /triage

    @Test
    void resubmitWithoutTriageIsNotFound() throws Exception {
        resubmit(newDependentToken(), body())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void resubmitWithOnlyRequiredQuestionsIsOk() throws Exception {
        String token = newDependentToken();
        submit(token, body()).andExpect(status().isCreated());

        resubmit(token, body())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.length()").value(requiredCount()));
    }

    @Test
    void resubmitIdentifyingQuestionsOnlyByCodeReplacesAnswers() throws Exception {
        String token = newDependentToken();
        submit(token, body()).andExpect(status().isCreated());

        resubmit(token, bodyByCode(Map.of("AGE_RANGE", "\"AGE_60_PLUS\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].question_code").value("AGE_RANGE"))
                .andExpect(jsonPath("$.answers[0].values[0]").value("AGE_60_PLUS"));
    }

    @Test
    void resubmitWithEmptyAnswersIsBadRequest() throws Exception {
        String token = newDependentToken();
        submit(token, body()).andExpect(status().isCreated());

        resubmit(token, "{\"answers\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("answers")));
    }

    @Test
    void resubmitWithMissingRequiredQuestionIsBadRequest() throws Exception {
        String token = newDependentToken();
        submit(token, body()).andExpect(status().isCreated());

        resubmit(token, bodyWithout(singleChoiceId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Missing required questions")))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(singleChoiceId))));
    }

    @Test
    void resubmitReplacesAnswersAndKeepsTriageId() throws Exception {
        String token = newDependentToken();
        submit(token, body(Map.of(
                singleChoiceId, "\"AGE_18_24\"",
                multiChoiceId, "\"OTHER\""), null))
                .andExpect(status().isCreated());

        // OTHER is sent again on purpose: the old row must be gone before the new one is inserted
        resubmit(token, body(Map.of(
                singleChoiceId, "\"AGE_25_39\"",
                multiChoiceId, "\"OTHER\", \"CONCERNED_USE\""), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].values[0]").value("AGE_25_39"))
                .andExpect(jsonPath("$.answers[1].values.length()").value(2));

        submit(token, body())
                .andExpect(status().isConflict());
    }

    // helpers

    private ResultActions submit(String token, String body) throws Exception {
        return mockMvc.perform(post("/triage")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions resubmit(String token, String body) throws Exception {
        return mockMvc.perform(put("/triage")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // an answer (first option) for every required question; overrides replace or add answers by question id
    private String body(Map<Long, String> overrides, Long skip) {
        Map<Long, String> values = new LinkedHashMap<>();
        for (QuestionTriage question : questions) {
            if (question.isRequired()) {
                values.put(question.getId(), "\"" + question.getOptions().get(0).get("code") + "\"");
            }
        }
        values.putAll(overrides);
        values.remove(skip);
        return "{\"answers\": [" + values.entrySet().stream()
                .map(e -> "{\"question_id\": %d, \"values\": [%s]}".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",")) + "]}";
    }

    private String body() {
        return body(Map.of(), null);
    }

    // like body(), but every answer names its question by code; overrides replace or add answers by question code
    private String bodyByCode(Map<String, String> overrides) {
        Map<String, String> values = new LinkedHashMap<>();
        for (QuestionTriage question : questions) {
            if (question.isRequired()) {
                values.put(question.getCode(), "\"" + question.getOptions().get(0).get("code") + "\"");
            }
        }
        values.putAll(overrides);
        return "{\"answers\": [" + values.entrySet().stream()
                .map(e -> "{\"question_code\": \"%s\", \"values\": [%s]}".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",")) + "]}";
    }

    private String bodyWithout(Long questionId) {
        return body(Map.of(), questionId);
    }

    private int requiredCount() {
        return (int) questions.stream().filter(QuestionTriage::isRequired).count();
    }

    private static String answerBody(Long questionId, String values) {
        return "{\"answers\": [{\"question_id\": %d, \"values\": [%s]}]}".formatted(questionId, values);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static Long firstOfType(List<QuestionTriage> questions, QuestionType type) {
        return questions.stream()
                .filter(q -> q.getQuestionType() == type)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private String newDependentToken() {
        User user = newUser(UserType.dependente);
        Dependent dependent = new Dependent();
        dependent.setUser(user);
        dependentRepository.save(dependent);
        return jwtService.generateToken(user);
    }

    private String newPsychologistToken() {
        User user = newUser(UserType.psicologo);
        Psychologist psychologist = new Psychologist();
        psychologist.setUser(user);
        psychologist.setCredentialType(CrmCrpType.CRP);
        psychologist.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 20));
        psychologistRepository.save(psychologist);
        return jwtService.generateToken(user);
    }

    private User newUser(UserType type) {
        User user = new User();
        user.setRealName("triage test");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        return userRepository.save(user);
    }
}
