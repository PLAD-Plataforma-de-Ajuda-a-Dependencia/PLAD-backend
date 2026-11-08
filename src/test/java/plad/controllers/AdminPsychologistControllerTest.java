package plad.controllers;

import com.jayway.jsonpath.JsonPath;
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
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.QuestionTriageRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /admin/psychologists (decision 32): admin only, newest first, real name and credential, never the e-mail and
// nothing about a dependent
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminPsychologistControllerTest {

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

    @Test
    void adminListsPsychologistsNewestFirstWithExactlyTheDocumentedKeys() throws Exception {
        User admin = newUser(UserType.admin, "Admin Listador");
        User older = newPsychologist("Psicologa Mais Antiga", false);
        User newer = newPsychologist("Psicologo Mais Novo", true);

        String response = read("/admin/psychologists?size=100", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andReturn().getResponse().getContentAsString();

        List<Integer> ids = JsonPath.read(response, "$.items[*].id");
        assertThat(ids.indexOf(newer.getId().intValue())).isGreaterThanOrEqualTo(0)
                .isLessThan(ids.indexOf(older.getId().intValue()));

        List<Map<String, Object>> shown = JsonPath.read(response, "$.items[?(@.id == %d)]".formatted(newer.getId()));
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).keySet())
                .containsExactlyInAnyOrder("id", "real_name", "credential_type", "crm_crp_number", "is_validated", "created_at");
        assertThat(shown.get(0)).containsEntry("real_name", "Psicologo Mais Novo")
                .containsEntry("credential_type", "CRP")
                .containsEntry("is_validated", true);
        List<Boolean> olderValidated = JsonPath.read(response, "$.items[?(@.id == %d)].is_validated".formatted(older.getId()));
        assertThat(olderValidated).containsExactly(false);
    }

    @Test
    void pagesWalkTheListOneAtATime() throws Exception {
        User admin = newUser(UserType.admin, "Admin Paginador");
        User older = newPsychologist("Psicologa Pagina Dois", false);
        User newer = newPsychologist("Psicologo Pagina Um", false);
        long total = psychologistRepository.count();

        read("/admin/psychologists?page=0&size=1", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total_elements").value(total))
                .andExpect(jsonPath("$.total_pages").value(total));
        read("/admin/psychologists?page=1&size=1", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(older.getId()))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void theListNeverCarriesAnEmail() throws Exception {
        User admin = newUser(UserType.admin, "Admin Sem Email");
        User psychologist = newPsychologist("Psicologa Sem Email", false);

        String body = read("/admin/psychologists?size=100", admin).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("Psicologa Sem Email")
                .doesNotContain(psychologist.getEmail())
                .doesNotContain(admin.getEmail())
                .doesNotContain("email");
    }

    @Test
    void theListCarriesNoDependentData() throws Exception {
        User admin = newUser(UserType.admin, "Admin Sem Dependente");
        newPsychologist("Psicologa Da Lista", false);
        String dependentName = "Dependente Nome Real Unico";
        User dependent = newDependent(dependentName);
        String nickname = dependentRepository.findById(dependent.getId()).orElseThrow().getNickname();
        submitTriage(dependent);

        String body = read("/admin/psychologists?size=100", admin).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(dependentName)
                .doesNotContain(nickname)
                .doesNotContain(dependent.getEmail())
                .doesNotContain("nickname");
        // only psychologists are counted: a dependent adds no row
        read("/admin/psychologists?size=1", admin)
                .andExpect(jsonPath("$.total_elements").value(psychologistRepository.count()));
    }

    @Test
    void psychologistAndDependentGetForbidden() throws Exception {
        User psychologist = newPsychologist("Psicologo Curioso", false);
        User dependent = newDependent("Dependente Curioso");

        read("/admin/psychologists", psychologist).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        read("/admin/psychologists", dependent).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void withoutATokenItIsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/psychologists")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidPagingIsBadRequest() throws Exception {
        User admin = newUser(UserType.admin, "Admin Paginas Ruins");

        read("/admin/psychologists?size=0", admin).andExpect(status().isBadRequest());
        read("/admin/psychologists?size=101", admin).andExpect(status().isBadRequest());
        read("/admin/psychologists?page=-1", admin).andExpect(status().isBadRequest());
    }

    private ResultActions read(String path, User reader) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + jwtService.generateToken(reader)));
    }

    // POST /triage as the dependent, with the first single-choice question
    private void submitTriage(User dependent) throws Exception {
        QuestionTriage choice = questionTriageRepository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(q -> q.getQuestionType() == QuestionType.single_choice).findFirst().orElseThrow();
        String body = "{\"answers\": [{\"question_code\": \"%s\", \"values\": [\"%s\"]}]}"
                .formatted(choice.getCode(), choice.getOptions().get(0).get("code"));
        mockMvc.perform(post("/triage")
                        .header("Authorization", "Bearer " + jwtService.generateToken(dependent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private User newPsychologist(String realName, boolean validated) {
        User user = newUser(UserType.psicologo, realName);
        Psychologist row = new Psychologist();
        row.setUser(user);
        row.setCredentialType(CrmCrpType.CRP);
        row.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 20));
        row.setValidate(validated);
        psychologistRepository.save(row);
        return user;
    }

    private User newDependent(String realName) {
        User user = newUser(UserType.dependente, realName);
        Dependent dependent = new Dependent();
        dependent.setNickname(plad.TestNicknames.unique());
        dependent.setUser(user);
        dependentRepository.save(dependent);
        return user;
    }

    private User newUser(UserType type, String realName) {
        User user = new User();
        user.setRealName(realName);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        return userRepository.save(user);
    }
}
