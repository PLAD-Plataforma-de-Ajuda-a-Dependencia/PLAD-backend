package plad.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.CrmCrpType;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.QuestionTriage;
import plad.entity.QuestionType;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.QuestionTriageRepository;
import plad.repositories.UserRepository;
import plad.services.ConsentService;
import plad.services.JwtService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /me/export: the logged-in user's own data (LGPD art. 18, II and V)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeExportTest {

    private static final String REAL_NAME = "Nome Real Exportavel Silva";
    private static final String FREE_TEXT = "texto-livre-do-dependente-8812";

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
    @Autowired
    private ConsentService consentService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dependentGetsAccountNicknamesConsentsAndTriageWithQuestionText() throws Exception {
        User user = newUser(UserType.dependente);
        Dependent dependent = newDependent(user);
        // a reroll leaves two history rows: the closed one and the one in use
        String oldNickname = dependent.getNickname();
        String newNickname = plad.TestNicknames.unique();
        dependentRepository.closeCurrentNicknameHistory(user.getId());
        dependentRepository.updateNicknameIfFree(user.getId(), newNickname);
        dependentRepository.openNicknameHistory(user.getId(), newNickname);
        consentService.record(user, "198.51.100.7");
        QuestionTriage single = question(QuestionType.single_choice);
        QuestionTriage free = question(QuestionType.free_text);
        submitTriage(user, single, free);

        mockMvc.perform(get("/me/export").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exported_at").isNotEmpty())
                .andExpect(jsonPath("$.account.id").value(user.getId()))
                .andExpect(jsonPath("$.account.email").value(user.getEmail()))
                .andExpect(jsonPath("$.account.type_user").value("dependente"))
                .andExpect(jsonPath("$.account.birth_date").value("1995-03-04"))
                .andExpect(jsonPath("$.account.gender").value("feminino"))
                .andExpect(jsonPath("$.dependent.nickname").value(newNickname))
                .andExpect(jsonPath("$.dependent.nickname_history", hasSize(2)))
                .andExpect(jsonPath("$.dependent.nickname_history[0].nickname").value(oldNickname))
                .andExpect(jsonPath("$.dependent.nickname_history[0].replaced_at").isNotEmpty())
                .andExpect(jsonPath("$.dependent.nickname_history[1].nickname").value(newNickname))
                .andExpect(jsonPath("$.dependent.nickname_history[1].replaced_at").doesNotExist())
                .andExpect(jsonPath("$.psychologist").doesNotExist())
                .andExpect(jsonPath("$.consents", hasSize(2)))
                .andExpect(jsonPath("$.consents[0].document").value("terms"))
                .andExpect(jsonPath("$.consents[0].ip").value("198.51.100.7"))
                .andExpect(jsonPath("$.consents[1].document").value("privacy"))
                .andExpect(jsonPath("$.triage.id").isNotEmpty())
                .andExpect(jsonPath("$.triage.created_at").isNotEmpty())
                .andExpect(jsonPath("$.triage.updated_at").isNotEmpty())
                .andExpect(jsonPath("$.triage.answers", hasSize(2)))
                .andExpect(jsonPath("$.triage.answers[?(@.question_code=='%s')].question_text".formatted(single.getCode()))
                        .value(single.getQuestionText()))
                .andExpect(jsonPath("$.triage.answers[?(@.question_code=='%s')].values[0]".formatted(free.getCode()))
                        .value(FREE_TEXT));
    }

    // a dependent is identified by nickname only; the password hash never leaves the server either
    @Test
    void dependentExportHasNoRealNameAndNoPasswordHash() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);

        mockMvc.perform(get("/me/export").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(REAL_NAME))))
                .andExpect(content().string(not(containsString("real_name"))))
                .andExpect(content().string(not(containsString(user.getPasswordHash()))))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void dependentWithoutTriageGetsNullTriage() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);

        mockMvc.perform(get("/me/export").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dependent.nickname").isNotEmpty());
    }

    @Test
    void psychologistGetsTheirOwnNameAndCredentials() throws Exception {
        User user = newUser(UserType.psicologo);
        Psychologist psychologist = new Psychologist();
        psychologist.setUser(user);
        psychologist.setCredentialType(CrmCrpType.CRP);
        psychologist.setCrmCrpNumber("06" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        psychologist.setGraduate("Psicologia");
        psychologistRepository.save(psychologist);
        consentService.record(user, "198.51.100.7");

        mockMvc.perform(get("/me/export").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.type_user").value("psicologo"))
                .andExpect(jsonPath("$.psychologist.real_name").value(REAL_NAME))
                .andExpect(jsonPath("$.psychologist.credential_type").value("CRP"))
                .andExpect(jsonPath("$.psychologist.crm_crp_number").value(psychologist.getCrmCrpNumber()))
                .andExpect(jsonPath("$.psychologist.graduate").value("Psicologia"))
                .andExpect(jsonPath("$.psychologist.is_validated").value(false))
                .andExpect(jsonPath("$.dependent").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.triage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.consents", hasSize(2)));
    }

    @Test
    void adminGetsOnlyTheCommonBlockAndConsents() throws Exception {
        User admin = newUser(UserType.admin);

        mockMvc.perform(get("/me/export").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.type_user").value("admin"))
                .andExpect(jsonPath("$.dependent").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.psychologist").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.triage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.consents", hasSize(0)));
    }

    // the user comes from the token: nothing of another account can show up
    @Test
    void theExportHoldsOnlyTheCallersRows() throws Exception {
        User mine = newUser(UserType.dependente);
        Dependent myDependent = newDependent(mine);
        User other = newUser(UserType.dependente);
        Dependent otherDependent = newDependent(other);
        consentService.record(other, "203.0.113.99");
        QuestionTriage single = question(QuestionType.single_choice);
        QuestionTriage free = question(QuestionType.free_text);
        submitTriage(other, single, free);

        mockMvc.perform(get("/me/export").header("Authorization", bearer(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(mine.getId()))
                .andExpect(jsonPath("$.dependent.nickname").value(myDependent.getNickname()))
                .andExpect(jsonPath("$.triage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.consents", hasSize(0)))
                .andExpect(content().string(not(containsString(other.getEmail()))))
                .andExpect(content().string(not(containsString(otherDependent.getNickname()))))
                .andExpect(content().string(not(containsString("203.0.113.99"))))
                .andExpect(content().string(not(containsString(FREE_TEXT))));
    }

    // reading your own data is not a third-party access: nothing goes to the audit log
    @Test
    void exportingWritesNoAuditEntry() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);

        mockMvc.perform(get("/me/export").header("Authorization", bearer(user))).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE subject_user_id = ? OR actor_id = ?",
                Long.class, user.getId(), user.getId())).isZero();
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/me/export"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // --- helpers

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private User newUser(UserType type) {
        User user = new User();
        user.setRealName(REAL_NAME);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$not.a.real.hash.just.a.marker.for.the.test.ABCDEFGHIJKLMNOP");
        user.setBirthDate(LocalDate.of(1995, 3, 4));
        user.setGender(Sex.feminino);
        user.setTypeUser(type);
        return userRepository.save(user);
    }

    private Dependent newDependent(User user) {
        Dependent dependent = new Dependent();
        dependent.setUser(user);
        dependent.setNickname(plad.TestNicknames.unique());
        dependent = dependentRepository.save(dependent);
        dependentRepository.openNicknameHistory(user.getId(), dependent.getNickname());
        return dependent;
    }

    private QuestionTriage question(QuestionType type) {
        return questionTriageRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(q -> q.getQuestionType() == type).findFirst().orElseThrow();
    }

    private void submitTriage(User user, QuestionTriage single, QuestionTriage free) throws Exception {
        ResultActions result = mockMvc.perform(post("/triage")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\": [{\"question_code\": \"%s\", \"values\": [\"%s\"]}, {\"question_code\": \"%s\", \"values\": [\"%s\"]}]}"
                        .formatted(single.getCode(), single.getOptions().get(0).get("code"), free.getCode(), FREE_TEXT)));
        result.andExpect(status().isCreated());
    }
}
