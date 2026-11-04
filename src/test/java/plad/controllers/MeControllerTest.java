package plad.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.CrmCrpType;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeControllerTest {

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

    @Test
    void dependentWithoutTriageSeesHasTriageFalseAndNoPsychologistFields() throws Exception {
        User user = newDependent();

        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.type_user").value("dependente"))
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.has_triage").value(false))
                .andExpect(jsonPath("$.nickname").value(dependentRepository.findById(user.getId()).orElseThrow().getNickname()))
                .andExpect(jsonPath("$.real_name").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.credential_type").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.crm_crp_number").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.is_validated").value(org.hamcrest.Matchers.nullValue()))
                // no consent on file for a user built straight in the database
                .andExpect(jsonPath("$.consent_pending").value(true))
                // every key is always sent
                .andExpect(jsonPath("$", hasKey("real_name")))
                .andExpect(jsonPath("$", hasKey("is_validated")))
                .andExpect(jsonPath("$", not(hasKey("password_hash"))));
    }

    @Test
    void dependentWhoSubmittedTheTriageSeesHasTriageTrue() throws Exception {
        User user = newDependent();

        mockMvc.perform(post("/triage")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"question_code\": \"AGE_RANGE\", \"values\": [\"AGE_25_39\"]}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.has_triage").value(true));
    }

    @Test
    void psychologistSeesCredentialsAndNoHasTriage() throws Exception {
        User user = newUser(UserType.psicologo, "Maria Psi");
        Psychologist psychologist = new Psychologist();
        psychologist.setUser(user);
        psychologist.setCredentialType(CrmCrpType.CRP);
        psychologist.setCrmCrpNumber("06" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        psychologistRepository.save(psychologist);

        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.type_user").value("psicologo"))
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(jsonPath("$.real_name").value("Maria Psi"))
                .andExpect(jsonPath("$.credential_type").value("CRP"))
                .andExpect(jsonPath("$.crm_crp_number").value(psychologist.getCrmCrpNumber()))
                .andExpect(jsonPath("$.is_validated").value(false))
                .andExpect(jsonPath("$.has_triage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$", hasKey("has_triage")))
                .andExpect(jsonPath("$.nickname").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$", hasKey("nickname")));
    }

    @Test
    void validatedPsychologistSeesIsValidatedTrue() throws Exception {
        User user = newUser(UserType.psicologo, "Joao Psi");
        Psychologist psychologist = new Psychologist();
        psychologist.setUser(user);
        psychologist.setCredentialType(CrmCrpType.CRM);
        psychologist.setCrmCrpNumber("12" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        psychologist.setValidate(true);
        psychologistRepository.save(psychologist);

        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential_type").value("CRM"))
                .andExpect(jsonPath("$.is_validated").value(true));
    }

    @Test
    void adminSeesOnlyTheCommonFields() throws Exception {
        User user = newUser(UserType.admin, "Admin");

        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.type_user").value("admin"))
                .andExpect(jsonPath("$.has_triage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.real_name").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.credential_type").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.crm_crp_number").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.is_validated").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void eachTokenSeesOnlyItsOwnAccount() throws Exception {
        User first = newDependent();
        User second = newDependent();

        mockMvc.perform(get("/me").header("Authorization", bearer(first)))
                .andExpect(jsonPath("$.id").value(first.getId()))
                .andExpect(jsonPath("$.email").value(first.getEmail()));
        mockMvc.perform(get("/me").header("Authorization", bearer(second)))
                .andExpect(jsonPath("$.id").value(second.getId()))
                .andExpect(jsonPath("$.email").value(second.getEmail()));
    }

    // an id sent by the client is ignored: the user always comes from the token
    @Test
    void idInTheRequestIsIgnored() throws Exception {
        User mine = newDependent();
        User other = newDependent();

        mockMvc.perform(get("/me").param("id", String.valueOf(other.getId()))
                        .param("user_id", String.valueOf(other.getId()))
                        .header("Authorization", bearer(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mine.getId()));
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/me").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // helpers

    private User newDependent() {
        User user = newUser(UserType.dependente, "Dependent Test");
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

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
