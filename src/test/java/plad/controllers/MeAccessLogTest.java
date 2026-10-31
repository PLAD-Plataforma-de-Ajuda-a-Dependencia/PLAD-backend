package plad.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.AuditAction;
import plad.entity.User;
import plad.entity.UserType;
import plad.services.AuditService;
import plad.services.JwtService;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// LGPD art. 18: the data subject sees when their triage was read and by which role, never by whom
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeAccessLogTest {

    private static final long PSYCHOLOGIST_ID = 424_242L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuditService auditService;

    private long me;
    private long someoneElse;

    @BeforeEach
    void recordEntries() {
        me = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
        someoneElse = me + 1;
        auditService.record(AuditAction.TRIAGE_SUBMITTED, me, UserType.dependente, me, "TRIAGE", 1L, null);
        auditService.record(AuditAction.TRIAGE_VIEWED, PSYCHOLOGIST_ID, UserType.psicologo, me, "TRIAGE", 1L, null);
        auditService.record(AuditAction.TRIAGE_VIEWED, 1L, UserType.admin, me, "TRIAGE", 1L, null);
        auditService.record(AuditAction.TRIAGE_VIEWED, PSYCHOLOGIST_ID, UserType.psicologo, someoneElse, "TRIAGE", 2L, null);
    }

    @Test
    void dependentSeesOnlyReadsOfTheirOwnTriageNewestFirst() throws Exception {
        call("/me/access-log", me, UserType.dependente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].actor_role").value("admin"))
                .andExpect(jsonPath("$.items[1].actor_role").value("psicologo"))
                .andExpect(jsonPath("$.items[0].action").value("TRIAGE_VIEWED"))
                .andExpect(jsonPath("$.items[0].occurred_at").isNotEmpty());
    }

    @Test
    void entriesShowTheRoleAndNeverWhoReadIt() throws Exception {
        call("/me/access-log", me, UserType.dependente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actor_id").doesNotExist())
                .andExpect(jsonPath("$.items[0].subject_user_id").doesNotExist())
                .andExpect(jsonPath("$.items[0].details").doesNotExist())
                .andExpect(jsonPath("$.items[0].id").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.actor_id == " + PSYCHOLOGIST_ID + ")]").isEmpty());
    }

    @Test
    void ownSubmissionIsNotAnAccessByAThirdParty() throws Exception {
        call("/me/access-log", me, UserType.dependente)
                .andExpect(jsonPath("$.items[?(@.action == 'TRIAGE_SUBMITTED')]").isEmpty());
    }

    @Test
    void anotherDependentSeesTheirOwnLogOnly() throws Exception {
        call("/me/access-log", someoneElse, UserType.dependente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].actor_role").value("psicologo"));

        call("/me/access-log", me + 2, UserType.dependente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void paginates() throws Exception {
        call("/me/access-log?size=1&page=1", me, UserType.dependente)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].actor_role").value("psicologo"));
        call("/me/access-log?size=101", me, UserType.dependente).andExpect(status().isBadRequest());
    }

    @Test
    void psychologistAndAdminGetForbidden() throws Exception {
        call("/me/access-log", PSYCHOLOGIST_ID, UserType.psicologo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        call("/me/access-log", 1L, UserType.admin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anonymousGetsUnauthorized() throws Exception {
        mockMvc.perform(get("/me/access-log"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions call(String path, long userId, UserType type) throws Exception {
        User user = new User();
        user.setId(userId);
        user.setTypeUser(type);
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + jwtService.generateToken(user)));
    }
}
