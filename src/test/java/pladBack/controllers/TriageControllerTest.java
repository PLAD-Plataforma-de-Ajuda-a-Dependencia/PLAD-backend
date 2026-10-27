package pladBack.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import pladBack.entity.CrmCrpType;
import pladBack.entity.Dependent;
import pladBack.entity.Psychologist;
import pladBack.entity.QuestionTriage;
import pladBack.entity.QuestionType;
import pladBack.entity.User;
import pladBack.entity.sex;
import pladBack.entity.userType;
import pladBack.repositories.dependentRepository;
import pladBack.repositories.psychologistRepository;
import pladBack.repositories.questionTriageRepository;
import pladBack.repositories.userRepository;
import pladBack.services.JwtService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TriageControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private userRepository userRepository;
    @Autowired
    private dependentRepository dependentRepository;
    @Autowired
    private psychologistRepository psychologistRepository;
    @Autowired
    private questionTriageRepository questionTriageRepository;

    private Long singleChoiceId;
    private Long multiChoiceId;
    private Long freeTextId;

    @BeforeEach
    void loadSeededQuestions() {
        List<QuestionTriage> questions = questionTriageRepository.findAllByOrderByDisplayOrderAsc();
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
                .andExpect(jsonPath("$[0].question_text").isNotEmpty())
                .andExpect(jsonPath("$[0].question_type").value("single_choice"))
                .andExpect(jsonPath("$[0].display_order").value(1))
                .andExpect(jsonPath("$[0].options[0].code").value("AGE_UNDER_18"))
                .andExpect(jsonPath("$[0].options[0].label").isNotEmpty());
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
        String body = """
                {"answers": [
                  {"question_id": %d, "values": ["REDUCE_OR_STOP", "OTHER"]},
                  {"question_id": %d, "values": ["AGE_18_24"]},
                  {"question_id": %d, "values": ["  quero apoio  "]}
                ]}""".formatted(multiChoiceId, singleChoiceId, freeTextId);

        submit(token, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.answers.length()").value(3))
                .andExpect(jsonPath("$.answers[0].question_id").value(singleChoiceId))
                .andExpect(jsonPath("$.answers[1].values.length()").value(2))
                .andExpect(jsonPath("$.answers[2].values[0]").value("quero apoio"));

        submit(token, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void submitAcceptsEmptyTriage() throws Exception {
        submit(newDependentToken(), "{\"answers\": []}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.length()").value(0));
    }

    @Test
    void submitAsPsychologistIsForbidden() throws Exception {
        submit(newPsychologistToken(), "{\"answers\": []}")
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
    void submitWithoutQuestionIdIsBadRequest() throws Exception {
        submit(newDependentToken(), "{\"answers\": [{\"values\": [\"AGE_18_24\"]}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("answers[0].question_id")));
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
        resubmit(newDependentToken(), "{\"answers\": []}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void resubmitReplacesAnswersAndKeepsTriageId() throws Exception {
        String token = newDependentToken();
        submit(token, """
                {"answers": [
                  {"question_id": %d, "values": ["AGE_18_24"]},
                  {"question_id": %d, "values": ["OTHER"]}
                ]}""".formatted(singleChoiceId, multiChoiceId))
                .andExpect(status().isCreated());

        // OTHER is sent again on purpose: the old row must be gone before the new one is inserted
        resubmit(token, """
                {"answers": [
                  {"question_id": %d, "values": ["AGE_25_39"]},
                  {"question_id": %d, "values": ["OTHER", "CONCERNED_USE"]}
                ]}""".formatted(singleChoiceId, multiChoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].values[0]").value("AGE_25_39"))
                .andExpect(jsonPath("$.answers[1].values.length()").value(2));

        submit(token, "{\"answers\": []}")
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
        User user = newUser(userType.dependente);
        Dependent dependent = new Dependent();
        dependent.setUser(user);
        dependentRepository.save(dependent);
        return jwtService.generateToken(user);
    }

    private String newPsychologistToken() {
        User user = newUser(userType.psicologo);
        Psychologist psychologist = new Psychologist();
        psychologist.setUser(user);
        psychologist.setCredentialType(CrmCrpType.CRP);
        psychologist.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 20));
        psychologistRepository.save(psychologist);
        return jwtService.generateToken(user);
    }

    private User newUser(userType type) {
        User user = new User();
        user.setReal_name("triage test");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword_hash("test-hash");
        user.setBirth_date(LocalDate.of(2000, 1, 1));
        user.setGender(sex.outro);
        user.setTypeUser(type);
        return userRepository.save(user);
    }
}
